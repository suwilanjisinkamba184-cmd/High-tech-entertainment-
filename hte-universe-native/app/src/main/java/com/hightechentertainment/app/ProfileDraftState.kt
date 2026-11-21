package com.hightechentertainment.app

/**
 * Keeps independently edited profile fields safe while the asynchronous profile
 * request completes. A user's edit to one field must not block hydration of others.
 */
internal data class ProfileDraftState(
    val name: String,
    val bio: String,
    val phone: String,
    val nameEdited: Boolean = false,
    val bioEdited: Boolean = false,
    val phoneEdited: Boolean = false
) {
    fun hydrate(profileName: String, profileBio: String, profilePhone: String) = copy(
        name = if (nameEdited) name else profileName,
        bio = if (bioEdited) bio else profileBio,
        phone = if (phoneEdited) phone else profilePhone
    )

    fun editName(value: String) = copy(name = value, nameEdited = true)
    fun editBio(value: String) = copy(bio = value, bioEdited = true)
    fun editPhone(value: String) = copy(phone = value, phoneEdited = true)
}
