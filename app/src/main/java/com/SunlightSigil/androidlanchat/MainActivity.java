package com.SunlightSigil.androidlanchat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final int PORT = 12345;
    private static final int CHUNK_SIZE = 64000;
    private static final int FILE_CHUNK_HEADER_SIZE = 1;
    private static final int MAX_UDP_SIZE = CHUNK_SIZE + FILE_CHUNK_HEADER_SIZE;
    private DatagramSocket udpSocket;
    private DatagramSocket receiveSocket;
    private InetAddress broadcastAddress;
    private EditText messageInput;
    private Button sendButton;
    private FloatingActionButton fabAdd;
    private FloatingActionButton fabImage;
    private FloatingActionButton fabFile;
    private Handler mainHandler;
    private LinearLayout chatLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        messageInput = findViewById(R.id.message_input);
        sendButton = findViewById(R.id.send_button);
        fabAdd = findViewById(R.id.fab_add);
        fabImage = findViewById(R.id.fab_image);
        fabFile = findViewById(R.id.fab_file);
        chatLayout = findViewById(R.id.chat_layout);

        mainHandler = new Handler(Looper.getMainLooper());

        try {
            udpSocket = new DatagramSocket();
            broadcastAddress = InetAddress.getByName("192.168.1.255");
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Socket Initialization Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Start thread to receive messages
        new Thread(this::receiveMessages).start();

        sendButton.setOnClickListener(v -> sendMessage());
        fabAdd.setOnClickListener(v -> toggleFabMenu());
        fabImage.setOnClickListener(v -> chooseImage());
        fabFile.setOnClickListener(v -> chooseFile());

        checkPermissions();
    }

    private void receiveMessages() {
        new Thread(() -> {
            try {
                // Bind to all available network interfaces
                receiveSocket = new DatagramSocket(PORT, InetAddress.getByName("0.0.0.0"));
                receiveSocket.setBroadcast(true);
                byte[] buffer = new byte[MAX_UDP_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                Log.d("UDP_RECEIVE", "Listening for incoming messages on port " + PORT);

                while (true) {
                    try {
                        receiveSocket.receive(packet);
                        int length = packet.getLength();
                        Log.d("UDP_RECEIVE", "Received packet of length: " + length);
                        byte[] receivedData = Arrays.copyOf(packet.getData(), length);
                        String message = new String(receivedData, 0, length);

                        Log.d("UDP_RECEIVE", "Received Data: " + message);
                        mainHandler.post(() -> {
                            chatLayout.addView(createChatBubble(message, true));
                            ScrollView scrollView = findViewById(R.id.chat_scroll_view);
                            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                        });
                    } catch (IOException e) {
                        Log.e("UDP_RECEIVE", "Receive Error: " + e.getMessage(), e);
                    }
                }
            } catch (IOException e) {
                Log.e("UDP_RECEIVE", "Socket Error: " + e.getMessage(), e);
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Socket Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (receiveSocket != null && !receiveSocket.isClosed()) {
                    receiveSocket.close();
                }
            }
        }).start();
    }

    private void sendMessage() {
        String message = messageInput.getText().toString();
        if (!message.isEmpty()) {
            new Thread(() -> {
                try {
                    DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), broadcastAddress, PORT);
                    udpSocket.send(packet);
                    runOnUiThread(() -> {
                        appendMessage("You: " + message, false);
                        messageInput.setText("");
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "Send Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }).start();
        }
    }

    private View createChatBubble(String message, boolean isIncoming) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View chatBubble = inflater.inflate(R.layout.chat_bubble, null);

        TextView messageText = chatBubble.findViewById(R.id.message_text);
        messageText.setText(message);

        if (isIncoming) {
            chatBubble.setBackgroundResource(R.drawable.chat_bubble_incoming);
        } else {
            chatBubble.setBackgroundResource(R.drawable.chat_bubble_outgoing);
        }

        return chatBubble;
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
                sendFile(uri, "image");
            } else if (requestCode == 2) {
                sendFile(uri, "file");
            }
        }
    }

    private void sendFile(Uri uri, String fileType) {
        new Thread(() -> {
            try {
                File file = new File(uri.getPath());
                FileInputStream fis = new FileInputStream(file);
                byte[] buf = new byte[CHUNK_SIZE];
                int bytesRead;
                int sequenceNumber = 0;

                while ((bytesRead = fis.read(buf)) != -1) {
                    byte[] chunk = Arrays.copyOf(buf, bytesRead);
                    ByteArrayOutputStream packetStream = new ByteArrayOutputStream();
                    packetStream.write(sequenceNumber);
                    packetStream.write(chunk);
                    byte[] packetData = packetStream.toByteArray();
                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length, broadcastAddress, PORT);
                    udpSocket.send(packet);
                    sequenceNumber++;
                }
                fis.close();
                runOnUiThread(() -> appendMessage("You sent a " + fileType + ": " + uri.getLastPathSegment(), false));
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Send File Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void appendMessage(String message, boolean isIncoming) {
        runOnUiThread(() -> {
            TextView messageView = new TextView(MainActivity.this);
            messageView.setText(message);
            messageView.setPadding(16, 8, 16, 8);
            messageView.setTextColor(isIncoming ? Color.BLACK : Color.WHITE);
            messageView.setBackgroundResource(isIncoming ? R.drawable.chat_bubble_incoming : R.drawable.chat_bubble_outgoing);
            messageView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            chatLayout.addView(messageView);

            ScrollView scrollView = findViewById(R.id.chat_scroll_view);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void checkPermissions() {
        if (checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.INTERNET}, 1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiveSocket != null && !receiveSocket.isClosed()) {
            receiveSocket.close();
        }
    }
}
