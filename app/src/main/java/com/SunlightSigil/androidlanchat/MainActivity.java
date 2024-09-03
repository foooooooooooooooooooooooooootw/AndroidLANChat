package com.SunlightSigil.androidlanchat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class MainActivity extends AppCompatActivity {

    private static final int PORT = 12345;
    private static final int MAX_UDP_SIZE = 64000; // Size in bytes
    private DatagramSocket udpSocket;
    private DatagramSocket receiveSocket;
    private InetAddress serverAddress;
    private EditText messageInput;
    private Button sendButton;
    private FloatingActionButton fabAdd;
    private FloatingActionButton fabImage;
    private FloatingActionButton fabFile;
    private TextView chatLog;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        messageInput = findViewById(R.id.message_input);
        sendButton = findViewById(R.id.send_button);
        fabAdd = findViewById(R.id.fab_add);
        fabImage = findViewById(R.id.fab_image);
        fabFile = findViewById(R.id.fab_file);
        chatLog = findViewById(R.id.chat_log);

        mainHandler = new Handler(Looper.getMainLooper());

        try {
            udpSocket = new DatagramSocket();
            serverAddress = InetAddress.getByName("192.168.1.255"); // Adjust to correct IP
        } catch (SocketException e) {
            // Handle SocketException specifically
            e.printStackTrace();
        } catch (IOException e) {
            // Handle IOException (including SocketException) specifically
            e.printStackTrace();
        }

        new Thread(() -> {
            try {
                receiveSocket = new DatagramSocket(PORT);
                byte[] buffer = new byte[MAX_UDP_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (true) {
                    receiveSocket.receive(packet);
                    String receivedData = new String(packet.getData(), 0, packet.getLength());
                    mainHandler.post(() -> chatLog.append("Received: " + receivedData + "\n"));
                }
            } catch (IOException e) {
                e.printStackTrace();
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Receive Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();

        sendButton.setOnClickListener(v -> sendMessage());

        fabAdd.setOnClickListener(v -> toggleFabMenu());
        fabImage.setOnClickListener(v -> chooseImage());
        fabFile.setOnClickListener(v -> chooseFile());

        checkPermissions();
    }

    private void sendMessage() {
        String message = messageInput.getText().toString();
        if (!message.isEmpty()) {
            new Thread(() -> {
                try {
                    DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), serverAddress, PORT);
                    udpSocket.send(packet);
                    mainHandler.post(() -> {
                        chatLog.append("You: " + message + "\n");
                        messageInput.setText("");
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    mainHandler.post(() ->
                            Toast.makeText(MainActivity.this, "Send Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }).start();
        }
    }

    private void toggleFabMenu() {
        if (fabImage.getVisibility() == View.GONE) {
            fabImage.show();
            fabFile.show();
            fabAdd.setImageResource(R.drawable.baseline_close_24);
        } else {
            fabImage.hide();
            fabFile.hide();
            fabAdd.setImageResource(R.drawable.baseline_add_24);
        }
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, 1);
    }

    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, 2);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (requestCode == 1) {
                // Handle image sending
                sendFile(uri, "image");
            } else if (requestCode == 2) {
                // Handle file sending
                sendFile(uri, "file");
            }
        }
    }

    private void sendFile(Uri uri, String fileType) {
        new Thread(() -> {
            try {
                File file = new File(uri.getPath());
                FileInputStream fis = new FileInputStream(file);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buf)) != -1) {
                    bos.write(buf, 0, bytesRead);
                }
                fis.close();
                byte[] fileBytes = bos.toByteArray();
                DatagramPacket packet = new DatagramPacket(fileBytes, fileBytes.length, serverAddress, PORT);
                udpSocket.send(packet);
                mainHandler.post(() -> chatLog.append("You sent a " + fileType + ": " + uri.getLastPathSegment() + "\n"));
            } catch (IOException e) {
                e.printStackTrace();
                mainHandler.post(() ->
                        Toast.makeText(MainActivity.this, "Send File Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void checkPermissions() {
        if (checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.INTERNET}, 1);
        }
    }
}