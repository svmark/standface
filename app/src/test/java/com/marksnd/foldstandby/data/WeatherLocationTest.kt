package com.marksnd.foldstandby.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WeatherLocationTest {

    private val cached = LocationLookup.Found(35.1, 129.0)
    private val current = LocationLookup.Found(37.4, 127.1)

    private fun resolve(
        hasPermission: Boolean = true,
        servicesOn: Boolean = true,
        cached: LocationLookup.Found? = null,
        cachedAgeMs: Long? = null,
        current: LocationLookup.Found? = null,
        onFetch: () -> Unit = {},
    ) = runBlocking {
        resolveLocation(hasPermission, servicesOn, cached, cachedAgeMs) { onFetch(); current }
    }

    @Test fun noPermissionNeverFetches() {
        var fetched = false
        assertEquals(LocationLookup.NoPermission, resolve(hasPermission = false, current = current) { fetched = true })
        assertFalse(fetched)
    }

    @Test fun freshCacheIsUsedWithoutFetching() {
        var fetched = false
        assertEquals(cached, resolve(cached = cached, cachedAgeMs = 60_000L, current = current) { fetched = true })
        assertFalse(fetched)
    }

    @Test fun missingCacheFetchesCurrentLocation() {
        assertEquals(current, resolve(current = current))
    }

    @Test fun staleCacheFetchesCurrentLocation() {
        assertEquals(current, resolve(cached = cached, cachedAgeMs = FRESH_LOCATION_MS + 1, current = current))
    }

    @Test fun staleCacheBeatsSeoulWhenFetchFails() {
        assertEquals(cached, resolve(cached = cached, cachedAgeMs = FRESH_LOCATION_MS + 1))
        assertEquals(cached, resolve(servicesOn = false, cached = cached, cachedAgeMs = FRESH_LOCATION_MS + 1))
    }

    @Test fun reasonsWhenNothingFound() {
        assertEquals(LocationLookup.ServicesOff, resolve(servicesOn = false, current = current))
        assertEquals(LocationLookup.NotFound, resolve())
    }

    @Test fun fallbackPlaceTellsWhy() {
        assertEquals("서울", fallbackPlace(LocationLookup.NoPermission, korean = true))
        assertEquals("서울 · 위치 꺼짐", fallbackPlace(LocationLookup.ServicesOff, korean = true))
        assertEquals("서울 · 위치 못 찾음", fallbackPlace(LocationLookup.NotFound, korean = true))
        assertEquals("Seoul", fallbackPlace(LocationLookup.NoPermission, korean = false))
    }
}
