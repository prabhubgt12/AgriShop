package com.fertipos.agroshop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import com.fertipos.agroshop.ui.auth.AuthViewModel

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit
) {
    val vm: AuthViewModel = hiltViewModel()
    val ui = vm.registerState.collectAsState()
    val username = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val confirm = remember { mutableStateOf("") }

    LaunchedEffect(ui.value.success) {
        if (ui.value.success) onRegistered()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Create Account")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = username.value, onValueChange = { username.value = it }, label = { Text("Username") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password.value, onValueChange = { password.value = it }, label = { Text("Password") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = confirm.value, onValueChange = { confirm.value = it }, label = { Text("Confirm Password") })
        Spacer(Modifier.height(8.dp))
        if (ui.value.error != null) {
            Text(text = ui.value.error!!)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.register(username.value, password.value, confirm.value) }) { Text("Register") }
    }
}
