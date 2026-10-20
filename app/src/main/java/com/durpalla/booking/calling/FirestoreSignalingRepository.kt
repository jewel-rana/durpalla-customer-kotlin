package com.durpalla.booking.calling

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore `calls/{callId}` — field names match driver contract exactly.
 * Doc is created by the backend Admin SDK; clients write offer/answer/status and candidates.
 * See docs/calling-contract.md and docs/firestore.rules.
 */
class FirestoreSignalingRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : SignalingRepository {

    private fun callRef(callId: String) = db.collection("calls").document(callId)

    override fun observeCall(callId: String): Flow<SignalingSnapshot> = callbackFlow {
        val reg = callRef(callId).addSnapshotListener { snap, err ->
            if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
            val d = snap.data ?: return@addSnapshotListener
            trySend(
                SignalingSnapshot(
                    callId = d["callId"] as? String ?: callId,
                    tripId = when (val t = d["tripId"]) {
                        is Number -> t.toInt()
                        is String -> t.filter { it.isDigit() }.toIntOrNull() ?: 0
                        else -> 0
                    },
                    status = CallStatus.fromWire(d["status"] as? String),
                    callerId = d["callerId"] as? String ?: "",
                    callerRole = d["callerRole"] as? String ?: CallRole.CUSTOMER.wire,
                    calleeId = d["calleeId"] as? String ?: "",
                    calleeRole = d["calleeRole"] as? String ?: CallRole.DRIVER.wire,
                    provider = CallProvider.fromWire(d["provider"] as? String),
                    offer = readSdp(d["offer"]),
                    answer = readSdp(d["answer"]),
                    endedBy = d["endedBy"] as? String,
                    reason = d["reason"] as? String,
                ),
            )
        }
        awaitClose { reg.remove() }
    }

    override suspend fun createCallDoc(
        callId: String,
        tripId: Int,
        callerId: String,
        callerRole: CallRole,
        calleeId: String,
        calleeRole: CallRole,
    ) {
        // Backend Admin SDK creates the doc on POST /calls (rules: create=false). No-op for production.
    }

    override suspend fun setOffer(callId: String, offer: SdpPayload) {
        // Parties may write offer only (ICE restart must not reset status to ringing).
        callRef(callId).set(
            mapOf("offer" to mapOf("type" to offer.type, "sdp" to offer.sdp)),
            SetOptions.merge(),
        ).await()
    }

    override suspend fun setAnswer(callId: String, answer: SdpPayload) {
        callRef(callId).set(
            mapOf(
                "answer" to mapOf("type" to answer.type, "sdp" to answer.sdp),
                "status" to CallStatus.ACCEPTED.wire,
                "answeredAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    override suspend fun setStatus(callId: String, status: CallStatus, endedBy: String?, reason: String?) {
        val map = mutableMapOf<String, Any>("status" to status.wire)
        if (status == CallStatus.ACCEPTED) map["answeredAt"] = FieldValue.serverTimestamp()
        if (status.isTerminal) {
            map["endedAt"] = FieldValue.serverTimestamp()
            endedBy?.let { map["endedBy"] = it }
            reason?.let { map["reason"] = it }
        }
        callRef(callId).set(map, SetOptions.merge()).await()
    }

    override suspend fun addCallerCandidate(callId: String, candidate: IceCandidatePayload) {
        callRef(callId).collection("callerCandidates").add(candidate.toMap()).await()
    }

    override suspend fun addCalleeCandidate(callId: String, candidate: IceCandidatePayload) {
        callRef(callId).collection("calleeCandidates").add(candidate.toMap()).await()
    }

    override fun observeRemoteCandidates(callId: String, asCaller: Boolean): Flow<IceCandidatePayload> = callbackFlow {
        val col = if (asCaller) "calleeCandidates" else "callerCandidates"
        val reg = callRef(callId).collection(col).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            for (change in snap.documentChanges) {
                if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                    readIce(change.document.data)?.let { trySend(it) }
                }
            }
        }
        awaitClose { reg.remove() }
    }

    private fun IceCandidatePayload.toMap() = mapOf(
        "candidate" to candidate,
        "sdpMid" to sdpMid,
        "sdpMLineIndex" to sdpMLineIndex,
    )

    private fun readSdp(raw: Any?): SdpPayload? {
        val m = raw as? Map<*, *> ?: return null
        val type = m["type"] as? String ?: return null
        val sdp = m["sdp"] as? String ?: return null
        return SdpPayload(type, sdp)
    }

    private fun readIce(d: Map<String, Any>): IceCandidatePayload? {
        val c = d["candidate"] as? String ?: return null
        return IceCandidatePayload(
            candidate = c,
            sdpMid = d["sdpMid"] as? String,
            sdpMLineIndex = (d["sdpMLineIndex"] as? Number)?.toInt() ?: 0,
        )
    }
}
