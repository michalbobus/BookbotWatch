package com.bookbotwatch.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookbotwatch.data.CheckRow
import com.bookbotwatch.data.Prefs
import com.bookbotwatch.data.RestorioWatch
import com.bookbotwatch.data.StockRow
import com.bookbotwatch.data.StockWatch
import com.bookbotwatch.data.WatchItem
import com.bookbotwatch.data.asEur
import com.bookbotwatch.data.asEurSigned
import com.bookbotwatch.notify.Notifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        setContent {
            BookbotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) { AppScreen() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            vm.toastShown()
        }
    }

    val pickTxt = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(vm::onTxtPicked) }

    val createTxt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? -> uri?.let(vm::onTxtCreated) }

    var showAddStock by remember { mutableStateOf(false) }
    if (showAddStock) {
        AddStockDialog(
            onDismiss = { showAddStock = false },
            onConfirm = { url, threshold ->
                vm.addStockWatch(url, threshold)
                showAddStock = false
            }
        )
    }

    var showAddItem by remember { mutableStateOf(false) }
    if (showAddItem) {
        AddItemDialog(
            onDismiss = { showAddItem = false },
            onConfirm = { volume, name, price ->
                vm.addItem(volume, name, price)
                showAddItem = false
            }
        )
    }

    var editItem by remember { mutableStateOf<WatchItem?>(null) }
    editItem?.let { item ->
        EditItemPriceDialog(
            item = item,
            onDismiss = { editItem = null },
            onConfirm = { price ->
                vm.updateItemPrice(item, price)
                editItem = null
            }
        )
    }

    var showAddRestorio by remember { mutableStateOf(false) }
    if (showAddRestorio) {
        AddRestorioDialog(
            onDismiss = { showAddRestorio = false },
            onConfirm = { url, threshold, price ->
                vm.addRestorioWatch(url, threshold, price)
                showAddRestorio = false
            }
        )
    }

    val openUrl: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = {
                    Column {
                        Text("BookBot hliadka", fontWeight = FontWeight.Bold)
                        Text(
                            lastCheckText(state.report?.timeMs),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(
                    onClick = { vm.checkNow() },
                    enabled = !state.running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Kontrolujem…")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Skontrolovať teraz", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            state.report?.error?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            err,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            item {
                SectionCard("Sledovaný zoznam", Icons.Default.Description) {
                    Text(
                        if (state.txtName.isBlank()) "Zatiaľ nie je vybraný žiadny TXT súbor."
                        else state.txtName,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${state.itemCount} položiek • diely a ceny sa dajú upravovať tu v appke",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            pickTxt.launch(arrayOf("text/plain", "text/*", "*/*"))
                        }) { Text("Vybrať TXT") }
                        OutlinedButton(onClick = { createTxt.launch("zoznam.txt") }) {
                            Text("Nový súbor")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.resetHistory() }) { Text("Vynulovať históriu") }

                    if (state.items.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        state.items.forEach { item ->
                            ItemEditRow(
                                item = item,
                                onEdit = { editItem = item },
                                onDelete = { vm.removeItem(item) }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { showAddItem = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pridať položku")
                    }
                }
            }

            val rows = state.report?.rows.orEmpty()
            if (rows.isNotEmpty()) {
                item {
                    Text(
                        "Výsledky",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
                items(rows) { row ->
                    ResultRow(row) { row.offer?.url?.let(openUrl) }
                }
            }

            item {
                SectionCard("Strážený sklad", Icons.Default.Inventory2) {
                    Text(
                        "Sleduje počet kusov skladom na konkrétnom vydaní. Upozorní, keď klesne pod prah.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    if (state.stockWatches.isEmpty()) {
                        Text(
                            "Zatiaľ žiadny strážený odkaz.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.stockWatches.forEach { watch ->
                            StockWatchRow(
                                watch = watch,
                                result = state.report?.stockRows?.firstOrNull { it.url == watch.url },
                                onOpen = { openUrl(watch.url) },
                                onThreshold = { vm.setStockThreshold(watch.url, it) },
                                onDelete = { vm.removeStockWatch(watch.url) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { showAddStock = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pridať odkaz")
                    }
                }
            }

            item {
                SectionCard("Restorio – sledovanie", Icons.Default.Storefront) {
                    Text(
                        "Sleduje konkrétne vydanie na restorio.sk – počet kusov aj cieľovú cenu. " +
                            "Upozorní, keď je skladom a/alebo cena klesne na cieľ.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    if (state.restorioWatches.isEmpty()) {
                        Text(
                            "Zatiaľ žiadny strážený odkaz.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.restorioWatches.forEach { watch ->
                            RestorioWatchRow(
                                watch = watch,
                                result = state.report?.restorioRows?.firstOrNull { it.url == watch.url },
                                onOpen = { openUrl(watch.url) },
                                onCountThreshold = { vm.setRestorioCountThreshold(watch.url, it) },
                                onPriceThreshold = { vm.setRestorioPriceThreshold(watch.url, it) },
                                onDelete = { vm.removeRestorioWatch(watch.url) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { showAddRestorio = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pridať odkaz")
                    }
                }
            }

            item {
                SectionCard("Stránka", Icons.Default.Link) {
                    OutlinedTextField(
                        value = state.url,
                        onValueChange = vm::onUrlChange,
                        label = { Text("URL výpisu na bookbot.sk") },
                        singleLine = false,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SectionCard("E-mail pri zlacnení", Icons.Default.MailOutline) {
                    SwitchRow(
                        "Posielať e-mail",
                        state.emailEnabled,
                        vm::onEmailEnabledChange
                    )
                    OutlinedTextField(
                        value = state.recipient,
                        onValueChange = vm::onRecipientChange,
                        label = { Text("Príjemca") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Odosielací SMTP účet (pre Gmail použi heslo aplikácie, nie bežné heslo)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.smtpHost,
                            onValueChange = vm::onSmtpHostChange,
                            label = { Text("SMTP server") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = state.smtpPort,
                            onValueChange = vm::onSmtpPortChange,
                            label = { Text("Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = state.smtpUser,
                        onValueChange = vm::onSmtpUserChange,
                        label = { Text("Odosielateľ / prihlásenie") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.smtpPass,
                        onValueChange = vm::onSmtpPassChange,
                        label = { Text("Heslo aplikácie") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { vm.sendTestEmail() },
                        enabled = !state.running
                    ) { Text("Poslať testovací e-mail") }
                }
            }

            item {
                SectionCard("Kontrola na pozadí", Icons.Default.Schedule) {
                    SwitchRow(
                        "Automatická kontrola",
                        state.autoCheck,
                        vm::onAutoCheckChange
                    )
                    Text(
                        "Interval",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 6, 12, 24).forEach { h ->
                            FilterChip(
                                selected = state.intervalHours == h,
                                onClick = { vm.onIntervalChange(h) },
                                label = { Text(if (h == 24) "1 deň" else "${h} h") }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Systém môže kontrolu posunúť podľa úspory batérie.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultRow(row: CheckRow, onClick: () -> Unit) {
    val offer = row.offer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (row.dropped) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        onClick = onClick
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (row.item.volume.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        row.item.volume,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.item.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    offer?.title ?: "na stránke sa nenašlo",
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (offer != null) {
                    Text(
                        offer.cents.asEur(),
                        fontWeight = FontWeight.Bold,
                        color = if (row.dropped) DropGreen else MaterialTheme.colorScheme.onSurface
                    )
                    val diff = row.diffCents
                    if (row.refCents != null && diff != null && diff != 0) {
                        Text(
                            row.refCents.asEur(),
                            fontSize = 11.sp,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        AssistChip(
                            onClick = onClick,
                            label = { Text(diff.asEurSigned(), fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = if (diff < 0) DropGreen else RiseRed,
                                containerColor = Color.Transparent
                            )
                        )
                    }
                } else {
                    Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StockWatchRow(
    watch: StockWatch,
    result: StockRow?,
    onOpen: () -> Unit,
    onThreshold: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val alert = result?.alert == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alert) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onOpen)
                ) {
                    Text(
                        result?.label?.takeIf { it.isNotBlank() }
                            ?: watch.url.removePrefix("https://bookbot.sk"),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val err = result?.error
                    val sub = when {
                        err != null -> err
                        result == null -> "zatiaľ neskontrolované"
                        else -> buildString {
                            append("skladom ${result.count} ks")
                            result.priceCents?.let { append(" • od ${it.asEur()}") }
                        }
                    }
                    Text(
                        sub,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (alert) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (result == null || result.count < 0) "?" else "${result.count} ks",
                    fontWeight = FontWeight.Bold,
                    color = if (alert) RiseRed else MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Odstrániť",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "upozorniť pod ${watch.threshold} ks",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { onThreshold(watch.threshold - 1) },
                    enabled = watch.threshold > 1
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Znížiť prah",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { onThreshold(watch.threshold + 1) }) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Zvýšiť prah",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddStockDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var threshold by remember { mutableStateOf(Prefs.DEFAULT_STOCK_THRESHOLD.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pridať strážený odkaz") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Odkaz na vydanie") },
                    placeholder = { Text("https://bookbot.sk/g/180963/b/22784943") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Upozorniť, keď klesne pod (ks)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url, threshold.toIntOrNull() ?: Prefs.DEFAULT_STOCK_THRESHOLD) },
                enabled = url.isNotBlank()
            ) { Text("Pridať") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zrušiť") }
        }
    )
}

@Composable
private fun ItemEditRow(
    item: WatchItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.label, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            item.refCents?.asEur() ?: "bez ceny",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Upraviť cenu",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Odstrániť",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var volume by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pridať položku") },
        text = {
            Column {
                OutlinedTextField(
                    value = volume,
                    onValueChange = { volume = it },
                    label = { Text("Diel (voliteľné, napr. V)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Názov") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Cena (voliteľné, napr. 8,49 €)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(volume, name, price) },
                enabled = name.isNotBlank()
            ) { Text("Pridať") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zrušiť") }
        }
    )
}

@Composable
private fun EditItemPriceDialog(
    item: WatchItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var price by remember { mutableStateOf(item.refCents?.asEur()?.removeSuffix(" €") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.label) },
        text = {
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text("Cena (napr. 8,49 €, prázdne = bez ceny)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(price) }) { Text("Uložiť") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zrušiť") }
        }
    )
}

@Composable
private fun RestorioWatchRow(
    watch: RestorioWatch,
    result: com.bookbotwatch.data.RestorioRow?,
    onOpen: () -> Unit,
    onCountThreshold: (Int) -> Unit,
    onPriceThreshold: (String) -> Unit,
    onDelete: () -> Unit
) {
    val alert = result?.alert == true
    var editingPrice by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alert) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onOpen)
                ) {
                    Text(
                        result?.label?.takeIf { it.isNotBlank() }
                            ?: watch.url.removePrefix("https://www.restorio.sk/"),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val err = result?.error
                    val sub = when {
                        err != null -> err
                        result == null -> "zatiaľ neskontrolované"
                        else -> buildString {
                            append(if (result.count > 0) "skladom" else "vypredané")
                            result.priceCents?.let { append(" • ${it.asEur()}") }
                        }
                    }
                    Text(
                        sub,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (alert) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Odstrániť",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "upozorniť pod ${watch.countThreshold} ks",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { onCountThreshold(watch.countThreshold - 1) },
                    enabled = watch.countThreshold > 0
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Znížiť prah",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { onCountThreshold(watch.countThreshold + 1) }) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Zvýšiť prah",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { editingPrice = true }
            ) {
                Text(
                    "cieľová cena: " + (watch.priceThresholdCents?.asEur() ?: "nesledovaná"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Upraviť cieľovú cenu",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (editingPrice) {
        var text by remember { mutableStateOf(watch.priceThresholdCents?.asEur()?.removeSuffix(" €") ?: "") }
        AlertDialog(
            onDismissRequest = { editingPrice = false },
            title = { Text("Cieľová cena") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("napr. 12,90 €, prázdne = nesledovať") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onPriceThreshold(text)
                    editingPrice = false
                }) { Text("Uložiť") }
            },
            dismissButton = {
                TextButton(onClick = { editingPrice = false }) { Text("Zrušiť") }
            }
        )
    }
}

@Composable
private fun AddRestorioDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var threshold by remember { mutableStateOf(Prefs.DEFAULT_RESTORIO_COUNT_THRESHOLD.toString()) }
    var price by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pridať odkaz na restorio.sk") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Odkaz alebo kód produktu") },
                    placeholder = { Text("https://www.restorio.sk/9788076794306") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Upozorniť, keď klesne pod (ks)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Cieľová cena (voliteľné, napr. 14,96 €)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        url,
                        threshold.toIntOrNull() ?: Prefs.DEFAULT_RESTORIO_COUNT_THRESHOLD,
                        price
                    )
                },
                enabled = url.isNotBlank()
            ) { Text("Pridať") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zrušiť") }
        }
    )
}

private fun lastCheckText(ms: Long?): String {
    if (ms == null || ms <= 0L) return "zatiaľ bez kontroly"
    val fmt = SimpleDateFormat("d.M.yyyy HH:mm", Locale("sk", "SK"))
    return "posledná kontrola: " + fmt.format(Date(ms))
}
