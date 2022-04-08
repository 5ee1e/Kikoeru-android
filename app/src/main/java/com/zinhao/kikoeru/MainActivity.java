package com.zinhao.kikoeru;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements MusicChangeListener,ServiceConnection,LrcRowChangeListener {
    private static final String TAG = "MainActivity";
    private RecyclerView recyclerView;
    private WorkAdapter workAdapter;
    private List<JSONObject> works;
    private RecyclerView.OnScrollListener scrollListener;
    private int page = 1;
    private int totalCount = 0;
    private int retryCount =0;

    private View bottomLayout;
    private Animation outAnim;
    private Animation inAnim;
    private boolean shouldShowAnim = true;
    private ImageView ivCover;
    private TextView tvTitle;
    private TextView tvWorkTitle;
    private ImageButton ibStatus;
    private ImageButton ibCloseOrOpen;

    private AsyncHttpClient.StringCallback apisCallback = new AsyncHttpClient.StringCallback() {
        @Override
        public void onCompleted(Exception e, AsyncHttpResponse asyncHttpResponse, String s) {
            Log.d(TAG, "onCompleted: "+s);
            if(asyncHttpResponse == null || asyncHttpResponse.code() !=200){
                Log.d(TAG, String.format("onCompleted:failed! "));
                ivCover.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        retryCount++;
                        setTitle(String.format("retry: %d",retryCount));
                        getNextPage();
                    }
                },3000);
                return;
            }
            try {
                JSONObject jsonObject = new JSONObject(s);
                JSONArray jsonArray = jsonObject.getJSONArray("works");
                totalCount = jsonObject.getJSONObject("pagination").getInt("totalCount");
                page = jsonObject.getJSONObject("pagination").getInt("currentPage") +1;
                page = Math.min(page,totalCount/12 + 1);
                for (int i = 0; i < jsonArray.length(); i++) {
                    works.add(jsonArray.getJSONObject(i));
                }
                runOnUiThread(new Runnable() {
                    @SuppressLint("DefaultLocale")
                    @Override
                    public void run() {
                        setTitle(String.format("下次加载第%d页 ,当前已加载%d项",page,works.size()));
                        if(workAdapter == null){
                            workAdapter = new WorkAdapter(works);
                            recyclerView.setLayoutManager(new GridLayoutManager(MainActivity.this,2));
                            recyclerView.setAdapter(workAdapter);
                            recyclerView.addOnScrollListener(scrollListener);
                        }else {
                            workAdapter.notifyItemInserted(works.size()-jsonArray.length());
                        }

                    }
                });
            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
            }

        }
    };
    private AudioService.CtrlBinder ctrlBinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recyclerView = findViewById(R.id.recyclerView);
        bottomLayout = findViewById(R.id.bottomLayout);
        ivCover = bottomLayout.findViewById(R.id.imageView);
        tvTitle = bottomLayout.findViewById(R.id.textView);
        tvWorkTitle = bottomLayout.findViewById(R.id.textView2);
        ibStatus = bottomLayout.findViewById(R.id.button);
        ibCloseOrOpen = bottomLayout.findViewById(R.id.imageButton);
        ibCloseOrOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleBottom();
            }
        });
        startService(new Intent(this,AudioService.class));
        bindService(new Intent(this, AudioService.class), this,BIND_AUTO_CREATE);
        outAnim = AnimationUtils.loadAnimation(this,R.anim.move_bottom_out);
        inAnim = AnimationUtils.loadAnimation(this,R.anim.move_bottom_in);
        scrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if(works.size()>=totalCount){
                    return;
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (!recyclerView.canScrollVertically(1)) {
                        getNextPage();
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && shouldShowAnim && bottomLayout.getVisibility() == View.VISIBLE){
                    toggleBottom();
                }else if(dy<0 && shouldShowAnim && bottomLayout.getVisibility() == View.GONE){
                    toggleBottom();
                }
            }
        };
        App app = (App) getApplication();
        String token = app.getValue("token","");
        if(token.isEmpty()){
            Api.doGetToken();
        }else {
            token = app.getValue("token","eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2FzbXIub25lIiwic3ViIjoiZ3Vlc3QiLCJhdWQiOiJodHRwczovL2FzbXIub25lL2FwaSIsIm5hbWUiOiJndWVzdCIsImdyb3VwIjoidXNlciIsImlhdCI6MTY0OTM4NzYyMCwiZXhwIjoxNjUwNTk3MjIwfQ.VlaW0xXSWPKq_1bgDo6Ry-VezunW06F-YrdmUq102BM");
            Api.init(token);
        }
        works = new ArrayList<>();
        Api.doGetWorks(1,apisCallback);
    }

    private void toggleBottom(){
        if (shouldShowAnim && bottomLayout.getVisibility() == View.VISIBLE){
            shouldShowAnim = false;
            bottomLayout.startAnimation(outAnim);
            bottomLayout.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bottomLayout.setVisibility(View.GONE);
                    shouldShowAnim = true;
                }
            },outAnim.getDuration());
        }else if(shouldShowAnim && bottomLayout.getVisibility() == View.GONE){
            shouldShowAnim = false;
            bottomLayout.setVisibility(View.VISIBLE);
            bottomLayout.startAnimation(inAnim);
            bottomLayout.postDelayed(new Runnable() {
                @Override
                public void run() {
                    shouldShowAnim = true;
                }
            },inAnim.getDuration());
        }
    }

    public void getNextPage() {
        Api.doGetWorks(page,apisCallback);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onAlbumChange(int rjNumber) {
        Glide.with(this).load(Api.HOST+String.format("/api/cover/%d?type=sam",rjNumber)).into(ivCover);
    }

    @Override
    public void onAudioChange(JSONObject audio) {
        try {
            tvTitle.setText(audio.getString("title"));
            tvWorkTitle.setText(audio.getString("workTitle"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStatusChange(int status) {
        if(status == 0){
            ibStatus.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        }else {
            ibStatus.setImageResource(R.drawable.ic_baseline_pause_24);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ctrlBinder = (AudioService.CtrlBinder)service;
        ctrlBinder.addMusicChangeListener(MainActivity.this);
        ibStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(ctrlBinder.getCtrl().getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING){
                    ctrlBinder.getCtrl().getTransportControls().pause();
                }else {
                    ctrlBinder.getCtrl().getTransportControls().play();
                }
            }
        });
        bottomLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if(ctrlBinder.getCurrentTitle().endsWith("mp4")){
                        startActivity(new Intent(MainActivity.this,VideoPlayerActivity.class));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        ctrlBinder.addMusicChangeListener(this);
        ctrlBinder.addLrcRowChangeListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    @Override
    public void onChange(Lrc.LrcRow currentRow) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setTitle(currentRow.content);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ctrlBinder.removeMusicChangeListener(this);
        ctrlBinder.removeLrcRowChangeListener(this);
        unbindService(this);
    }


}