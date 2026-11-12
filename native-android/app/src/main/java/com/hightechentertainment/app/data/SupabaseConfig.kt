package com.hightechentertainment.app.data

/**
 * Public client configuration only. Supabase publishable keys are designed for client use;
 * authorization must always be enforced by PostgreSQL RLS and server-side functions.
 *
 * Never add a service_role key, sb_secret key, database password, or signing secret here.
 */
internal object SupabaseConfig {
    const val BASE_URL = "https://wqrxacjbsldbnoekbkbf.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_hB_aRh-dnicpRQq3wiRtWg_JTdhmMda"
}
