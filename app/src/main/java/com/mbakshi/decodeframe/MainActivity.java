package com.mbakshi.decodeframe;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.mbakshi.decodeframe.FrameResources.Util.Media.MediaFormat;
import com.mbakshi.decodeframe.FrameResources.Util.Media.MimeTypes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "DecodeFrameActivity";
    private static final int GET_VIDEO_FILE = 1;

    private Uri fileUri;
    private String filePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        addButton();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == GET_VIDEO_FILE && resultCode == RESULT_OK) {
            fileUri = data.getData();
            filePath = fileUri.getPath();
            extractFrame();
        }
    }

    private void addButton() {
        Button addMediaButton = (Button) findViewById(R.id.addMedia);
        addMediaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("file/*");
                startActivityForResult(intent, GET_VIDEO_FILE);
            }
        });
    }

    private void extractFrame() {
        final Context context = this;
        Thread thumbThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Extract frame start for file path " + filePath);
                ClipExtractor clipExtractor = new ClipExtractor(context);
                try {
                    if(clipExtractor.setDataSource(fileUri)) {
                        int trackCount = clipExtractor.getTrackCount();
                        MediaFormat format = null;
                        for(int i = 0; i < trackCount; i++) {
                            MediaFormat trackFormat = clipExtractor.getTrackFormat(i);
                            if(MimeTypes.isVideo(trackFormat.mimeType)) {
                                format = trackFormat;
                                break;
                            }
                        }
                        if(format != null) {
                            int w = format.width;
                            int h = format.height;
                            Bitmap bitmap = clipExtractor.getFrameAtTime(0, w, h, false);
                            saveBitmap(bitmap);
                        }
                        else {
                            Log.e(TAG, "Could not find video track in file");
                        }
                    }
                }
                catch (IOException exc) {
                    Log.e(TAG, "IOExc could not set data source " + exc);
                }
            }
        });
        thumbThread.start();
    }

    private void saveBitmap(Bitmap bitmap) {
        String file_path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/thumb.png";
        File file = new File(file_path);
        try {
            FileOutputStream fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, fOut);
            fOut.flush();
            fOut.close();
        }
        catch (IOException ioexc) {
            Log.e(TAG, "IOexc " + ioexc);
        }
    }
}
