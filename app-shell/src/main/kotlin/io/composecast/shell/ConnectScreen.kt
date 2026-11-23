package io.composecast.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The Preview app before a server is chosen (T-045): how to connect (QR code in the admin panel →
 * the server's `/connect` page) or type the address.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectScreen(initialAddress: String = "", onConnect: (String) -> Unit) {
    var address by rememberSaveable(initialAddress) { mutableStateOf(initialAddress) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    fun connect() {
        val url = PreviewLink.normalize(address)
        invalid = url == null
        if (url != null) onConnect(url)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                title = { Text(stringResource(R.string.app_name)) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Connect to your server", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Scan the QR code in your admin panel with the camera, then tap " +
                    "\"Open in compose-cast Preview\". Or type your server's address:",
            )
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it
                    invalid = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Server address") },
                placeholder = { Text("https://app.example.com") },
                singleLine = true,
                isError = invalid,
                supportingText = if (invalid) {
                    { Text("Enter an address like https://app.example.com or http://192.168.1.10:8080") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { connect() }),
            )
            Button(onClick = ::connect) { Text("Connect") }
        }
    }
}
