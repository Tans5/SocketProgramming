package com.tans.socketprogramming

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        as_server_bt.setOnClickListener {
            startActivity(Intent(this, ServerActivity::class.java))
        }

        as_client_bt.setOnClickListener {
            startActivity(Intent(this, ClientActivity::class.java))
        }
    }
}
