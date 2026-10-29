package at.akrea.moonphase.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import at.akrea.moonphase.R
import at.akrea.moonphase.data.AppLanguage
import at.akrea.moonphase.data.AppSettings
import at.akrea.moonphase.data.Cities
import at.akrea.moonphase.data.LocationMode
import at.akrea.moonphase.data.Place
import at.akrea.moonphase.location.DeviceLocation
import at.akrea.moonphase.widget.MoonWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

@Composable
fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(settings)
    var locating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }

    fun locate() {
        scope.launch {
            locating = true
            message = null
            val location = DeviceLocation.fetch(context)
            locating = false
            if (location == null) {
                message = R.string.location_failed
            } else {
                onChange(
                    current.copy(
                        mode = LocationMode.GPS,
                        gpsLatitude = location.latitude,
                        gpsLongitude = location.longitude,
                        gpsTime = location.time,
                    ),
                )
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locate() else message = R.string.permission_denied
    }

    fun requestLocation() {
        if (DeviceLocation.hasPermission(context)) locate() else permission.launch(DeviceLocation.permissions)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Section(R.string.settings_location) {
                Text(
                    stringResource(R.string.settings_location_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Sky.Muted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                RadioRow(settings.mode == LocationMode.GPS, R.string.mode_device, R.string.mode_device_hint) {
                    if (settings.mode != LocationMode.GPS) {
                        onChange(settings.copy(mode = LocationMode.GPS))
                        if (settings.gpsLatitude == null || !DeviceLocation.hasPermission(context)) requestLocation()
                    }
                }
                if (settings.mode == LocationMode.GPS) {
                    DevicePanel(settings, locating, message, ::requestLocation)
                }
                RadioRow(settings.mode == LocationMode.PLACE, R.string.mode_place, R.string.mode_place_hint) {
                    message = null
                    onChange(settings.copy(mode = LocationMode.PLACE))
                }
                if (settings.mode == LocationMode.PLACE) {
                    PlacePicker(settings.place) { onChange(current.copy(mode = LocationMode.PLACE, place = it)) }
                }
            }
            Section(R.string.settings_widget) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onChange(settings.copy(showWidgetLabel = !settings.showWidgetLabel)) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.show_widget_label), Modifier.weight(1f))
                    Switch(settings.showWidgetLabel, { onChange(settings.copy(showWidgetLabel = it)) })
                }
            }
            Section(R.string.settings_language) {
                LanguagePicker()
            }
            Section(R.string.settings_about, divider = false) {
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
                }
                Text(stringResource(R.string.attribution_moon), style = MaterialTheme.typography.bodySmall, color = Sky.Muted)
                Text(
                    stringResource(R.string.attribution_cities),
                    style = MaterialTheme.typography.bodySmall,
                    color = Sky.Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    stringResource(R.string.version, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = Sky.Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: Int, divider: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = Sky.Moonlight,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
    Column(content = content)
    if (divider) HorizontalDivider(Modifier.padding(top = 16.dp), color = Sky.Line)
}

@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppLanguage.get(context)) }
    listOf(
        AppLanguage.SYSTEM to R.string.language_system,
        "en" to R.string.language_en,
        "de" to R.string.language_de,
    ).forEach { (tag, title) ->
        RadioRow(language == tag, title) {
            if (language != tag) {
                language = tag
                AppLanguage.set(context, tag)
                MoonWidgetProvider.refresh(context)
                if (Build.VERSION.SDK_INT < 33) context.findActivity()?.recreate()
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun RadioRow(selected: Boolean, title: Int, hint: Int? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick)
        Column(Modifier.padding(start = 4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = Sky.Muted) }
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Sky.Panel)
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun DevicePanel(settings: AppSettings, locating: Boolean, message: Int?, onUpdate: () -> Unit) {
    val formats = rememberFormats()
    Panel {
        val lat = settings.gpsLatitude
        val lon = settings.gpsLongitude
        if (lat != null && lon != null) {
            Text(
                stringResource(
                    R.string.coordinates,
                    abs(lat), stringResource(if (lat >= 0) R.string.north else R.string.south),
                    abs(lon), stringResource(if (lon >= 0) R.string.east else R.string.west),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.updated_at, formats.dateTime.format(Instant.ofEpochMilli(settings.gpsTime).atZone(ZoneId.systemDefault()))),
                style = MaterialTheme.typography.bodySmall,
                color = Sky.Muted,
            )
        } else {
            Text(stringResource(R.string.no_position), style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            stringResource(R.string.time_zone, ZoneId.systemDefault().id),
            style = MaterialTheme.typography.bodySmall,
            color = Sky.Muted,
        )
        OutlinedButton(onClick = onUpdate, enabled = !locating, modifier = Modifier.padding(top = 10.dp)) {
            if (locating) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.locating))
            } else {
                Text(stringResource(R.string.update_location))
            }
        }
        message?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = Sky.Error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun PlacePicker(place: Place?, onPick: (Place) -> Unit) {
    val context = LocalContext.current
    val formats = rememberFormats()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Place>()) }
    val cities by produceState<Cities?>(null) {
        value = withContext(Dispatchers.IO) { Cities.load(context) }
    }
    LaunchedEffect(query, cities) {
        val loaded = cities ?: return@LaunchedEffect
        delay(120)
        results = withContext(Dispatchers.Default) { loaded.search(query) }
    }

    Panel {
        if (place != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Icon(Icons.Outlined.Place, null, tint = Sky.Moonlight, modifier = Modifier.size(18.dp))
                Column(Modifier.padding(start = 8.dp)) {
                    Text(place.label(formats.locale), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.time_zone, place.zoneId), style = MaterialTheme.typography.bodySmall, color = Sky.Muted)
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_city)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, null) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        val searching = query.trim().length >= 2
        when {
            searching && cities == null -> Hint(R.string.loading_cities)
            searching && results.isEmpty() -> Hint(R.string.no_city)
            searching -> results.forEach { result ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onPick(result)
                            query = ""
                            focus.clearFocus()
                        }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    Text(result.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        result.label(formats.locale).removePrefix(result.name).removePrefix(", ") + " · " + result.zoneId,
                        style = MaterialTheme.typography.bodySmall,
                        color = Sky.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = Sky.Muted, modifier = Modifier.padding(top = 10.dp))
}
