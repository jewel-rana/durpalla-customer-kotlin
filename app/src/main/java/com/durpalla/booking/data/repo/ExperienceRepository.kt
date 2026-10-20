package com.durpalla.booking.data.repo

import com.durpalla.booking.R
import kotlinx.coroutines.delay

/**
 * Tour & Entertainment have NO backend endpoints in the Flutter app yet.
 * TODO(backend): replace [MockExperienceRepository] with an API-backed implementation once
 *  e.g. `GET tour/search`, `GET tour/{id}`, `GET event/search`, `POST event/book` exist.
 */
interface ExperienceRepository {
    suspend fun list(service: Service, query: String = ""): List<Experience>
    suspend fun details(id: Int): Experience?
}

class MockExperienceRepository : ExperienceRepository {
    private val items = listOf(
        Experience(1, Service.TOUR, "Sajek Valley 2D/1N", "Rangamati", "2 days", 6500.0, 4.8, R.drawable.photo_sajek, listOf("Jeep transfer", "Cottage stay", "Sunrise at Konglak"), "Every Thu"),
        Experience(2, Service.TOUR, "Sundarbans river cruise", "Khulna", "3 days", 12500.0, 4.9, R.drawable.photo_sundarbans, listOf("Forest guide", "All meals", "Kotka beach"), "Fri – Sun"),
        Experience(3, Service.TOUR, "St. Martin's island escape", "Teknaf", "3 days", 9800.0, 4.7, R.drawable.photo_stmartin, listOf("Ship tickets", "Resort", "Chera Dwip"), "Weekly"),
        Experience(4, Service.TOUR, "Ratargul swamp & tea gardens", "Sylhet", "2 days", 5200.0, 4.6, R.drawable.photo_ratargul, listOf("Boat ride", "Tea estate walk"), "Daily"),
        Experience(5, Service.ENTERTAINMENT, "River Sound Live 2026", "Dhaka", "Evening", 1500.0, 4.5, R.drawable.photo_concert, listOf("Open-air concert", "Food court"), "Nov 14, 7:00 PM"),
        Experience(6, Service.ENTERTAINMENT, "Fantasy Funfair day pass", "Ashulia", "Full day", 900.0, 4.4, R.drawable.photo_park, listOf("All rides", "Water zone"), "Daily"),
        Experience(7, Service.ENTERTAINMENT, "Buriganga sunset kayak", "Dhaka", "2 hours", 1200.0, 4.6, R.drawable.photo_kayak, listOf("Gear included", "Instructor"), "Sat & Sun"),
        Experience(8, Service.TOUR, "Houseboat on Tanguar Haor", "Sunamganj", "2 days", 8500.0, 4.8, R.drawable.photo_houseboat, listOf("Houseboat", "Meals", "Watchtower"), "Fri"),
    )
    override suspend fun list(service: Service, query: String) = run { delay(250); items.filter { it.service == service && (query.isBlank() || it.title.contains(query, true) || it.location.contains(query, true)) } }
    override suspend fun details(id: Int) = items.firstOrNull { it.id == id }
}
