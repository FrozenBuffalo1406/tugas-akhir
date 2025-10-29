package com.tugasakhir.healtyheart.models

data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: String, // e.g., "Patient", "Relative"
    val profilePictureUrl: String = "" // Default empty string
)