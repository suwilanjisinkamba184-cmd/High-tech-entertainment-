package com.hightechentertainment.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileDraftStateTest {
    @Test
    fun editingNameDoesNotBlockHydrationOfBioAndPhone() {
        val draft = ProfileDraftState("", "", "")
            .editName("Updated name")
            .hydrate("Saved name", "Existing bio", "+260 971 000000")

        assertEquals("Updated name", draft.name)
        assertEquals("Existing bio", draft.bio)
        assertEquals("+260 971 000000", draft.phone)
    }

    @Test
    fun editingOneFieldPreservesOnlyThatFieldAcrossLaterRefreshes() {
        val draft = ProfileDraftState("Name", "Bio", "Phone")
            .editBio("New bio")
            .hydrate("Server name", "Server bio", "Server phone")
            .hydrate("Latest name", "Latest server bio", "Latest phone")

        assertEquals("Latest name", draft.name)
        assertEquals("New bio", draft.bio)
        assertEquals("Latest phone", draft.phone)
    }

    @Test
    fun editsToAllFieldsAreNeverOverwrittenByLateHydration() {
        val draft = ProfileDraftState("", "", "")
            .editName("N")
            .editBio("B")
            .editPhone("P")
            .hydrate("server N", "server B", "server P")

        assertEquals("N", draft.name)
        assertEquals("B", draft.bio)
        assertEquals("P", draft.phone)
    }
}
