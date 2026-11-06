package id.playandlearn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Placeholder Increment 0; layar anak zero-text dibangun di Increment 1.
        setContent { Text("Play & Learn") }
    }
}
