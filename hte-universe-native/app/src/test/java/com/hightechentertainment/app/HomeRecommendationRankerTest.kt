package com.hightechentertainment.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRecommendationRankerTest {
    @Test fun favouriteGenresRankAheadOfUnrelatedGenres() {
        assertEquals(listOf(1, 3, 2, 0), rankGenreIndexesByPreference(listOf("Drill", "Afrobeat", "Gospel", "AFROBEAT"), mapOf("afrobeat" to 2, "gospel" to 1)))
    }
    @Test fun equalScoresPreserveCatalogueOrder() {
        assertEquals(listOf(0, 1, 2), rankGenreIndexesByPreference(listOf("Dancehall", "Drill", "Gospel"), mapOf("dancehall" to 1, "drill" to 1, "gospel" to 1)))
    }
    @Test fun noPreferenceKeepsOriginalOrder() {
        assertEquals(listOf(0, 1, 2), rankGenreIndexesByPreference(listOf("Drill", "Gospel", null), emptyMap()))
    }
}
