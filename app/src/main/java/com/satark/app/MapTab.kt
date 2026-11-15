package com.satark.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

@Composable fun MapTab() {
    val ctx = LocalContext.current
    val (users, _) = rememberPoll(listOf<Profile>(), 10000) { Supa.users() }
    val located = users.filter { it.lat != null && it.lng != null }
    val fitted = remember { booleanArrayOf(false) }
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osm"); osmdroidTileCache = File(ctx.cacheDir, "osm/tiles")
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true)
            controller.setZoom(5.0); controller.setCenter(GeoPoint(22.5, 79.0))
        }
    }
    DisposableEffect(Unit) { map.onResume(); onDispose { map.onPause(); map.onDetach() } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp)) { T("Map", 26, bold = true); T("${located.size} user(s) ne location share ki hai", 13, Soft) }
        AndroidView(factory = { map }, modifier = Modifier.fillMaxWidth().weight(1f), update = { m ->
            m.overlays.clear()
            located.forEach { u ->
                m.overlays.add(Marker(m).apply {
                    position = GeoPoint(u.lat!!, u.lng!!); title = u.name
                    snippet = "${u.task.ifBlank { "—" }} • ${tm(u.locMs)}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            if (located.isNotEmpty() && !fitted[0]) {
                fitted[0] = true
                if (located.size == 1) { m.controller.setZoom(14.0); m.controller.setCenter(GeoPoint(located[0].lat!!, located[0].lng!!)) }
                else m.post { m.zoomToBoundingBox(BoundingBox.fromGeoPoints(located.map { GeoPoint(it.lat!!, it.lng!!) }), false, 100) }
            }
            m.invalidate()
        })
    }
}
