package com.mdrafi.freechat;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {
    LinearLayout root, messages; Spinner modelSpinner; EditText input; TextView status;
    ArrayList<String> models=new ArrayList<>(); ArrayList<String> modelIds=new ArrayList<>();
    android.content.SharedPreferences prefs;
    final String BASE="https://vyceai.com";

    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.WHITE); t.setPadding(dp(12),dp(8),dp(12),dp(8)); return t; }
    Button btn(String s){ Button b=new Button(this); b.setText(s); return b; }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(18,18,18));
        getWindow().setNavigationBarColor(Color.rgb(18,18,18));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs=getSharedPreferences("freechat",0);
        build();
        applySystemInsets();
        loadModels();
    }

    void applySystemInsets(){
        root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            v.setPadding(0,bars.top,0,bars.bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(18,18,18));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=tv("Free Chat",20); top.addView(title,new LinearLayout.LayoutParams(0,dp(58),1));
        Button newChat=btn("New Chat"); top.addView(newChat,new LinearLayout.LayoutParams(dp(105),dp(58)));
        Button settings=btn("⚙"); top.addView(settings,new LinearLayout.LayoutParams(dp(60),dp(58)));
        root.addView(top);
        modelSpinner=new Spinner(this); root.addView(modelSpinner,new LinearLayout.LayoutParams(-1,dp(52)));
        status=tv("Loading models…",13); root.addView(status);
        ScrollView sv=new ScrollView(this); messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); sv.addView(messages); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bar=new LinearLayout(this); input=new EditText(this); input.setHint("Message…"); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.GRAY); input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE); bar.addView(input,new LinearLayout.LayoutParams(0,dp(60),1)); Button send=btn("Send"); bar.addView(send,new LinearLayout.LayoutParams(dp(90),dp(60))); root.addView(bar);
        setContentView(root);
        newChat.setOnClickListener(v->{messages.removeAllViews();});
        settings.setOnClickListener(v->showSettings());
        send.setOnClickListener(v->send());
    }

    void loadModels(){
        new Thread(()->{
            try{
                String key=prefs.getString("key","");
                if(key.isEmpty()){runOnUiThread(()->status.setText("Add your Vyce API key in ⚙ Settings")); return;}
                HttpsURLConnection c=(HttpsURLConnection)new URL(BASE+"/v1/models").openConnection();
                c.setRequestProperty("Authorization","Bearer "+key); c.setConnectTimeout(15000); c.setReadTimeout(15000);
                String body=read(c); JSONObject o=new JSONObject(body); JSONArray a=o.getJSONArray("data");
                models.clear(); modelIds.clear();
                for(int i=0;i<a.length();i++){JSONObject m=a.getJSONObject(i); String id=m.optString("id"); if(!id.isEmpty()){modelIds.add(id); models.add(id);}}
                runOnUiThread(()->{modelSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,models)); status.setText(models.size()+" models available");});
            }catch(Exception e){runOnUiThread(()->status.setText("Model load failed: "+e.getMessage()));}
        }).start();
    }

    void send(){
        String q=input.getText().toString().trim(); if(q.isEmpty())return;
        String key=prefs.getString("key","");
        if(key.isEmpty()){showSettings(); return;}
        addMessage("You",q); input.setText("");
        String model=modelIds.size()>modelSpinner.getSelectedItemPosition()?modelIds.get(modelSpinner.getSelectedItemPosition()):"";
        new Thread(()->{
            try{
                HttpsURLConnection c=(HttpsURLConnection)new URL(BASE+"/v1/chat/completions").openConnection();
                c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Authorization","Bearer "+key); c.setRequestProperty("Content-Type","application/json");
                JSONArray msgs=new JSONArray(); JSONObject msg=new JSONObject(); msg.put("role","user"); msg.put("content",q); msgs.put(msg);
                JSONObject req=new JSONObject(); req.put("model",model); req.put("messages",msgs); req.put("stream",false);
                OutputStream os=c.getOutputStream(); os.write(req.toString().getBytes("UTF-8")); os.close();
                String body=read(c); JSONObject o=new JSONObject(body); String ans=o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","No response");
                runOnUiThread(()->addMessage(model,ans));
            }catch(Exception e){runOnUiThread(()->addMessage("Error",e.getMessage()));}
        }).start();
    }

    String read(HttpsURLConnection c)throws Exception{InputStream is=c.getResponseCode()>=400?c.getErrorStream():c.getInputStream(); BufferedReader r=new BufferedReader(new InputStreamReader(is)); StringBuilder s=new StringBuilder(); String l; while((l=r.readLine())!=null)s.append(l); r.close(); return s.toString();}

    void addMessage(String who,String text){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        TextView h=tv(who,12); TextView b=tv(text,16); b.setTextIsSelectable(true);
        box.addView(h); box.addView(b);
        Button copy=btn("Copy"); copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("response",text)); Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();});
        box.addView(copy,new LinearLayout.LayoutParams(-2,dp(45))); messages.addView(box);
    }

    void showSettings(){
        LinearLayout l=new LinearLayout(this); l.setPadding(dp(20),dp(10),dp(20),dp(10)); l.setOrientation(LinearLayout.VERTICAL);
        EditText key=new EditText(this); key.setHint("Vyce API key"); key.setText(prefs.getString("key","")); key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); l.addView(key);
        l.addView(tv("Plugins",18)); l.addView(tv("GitHub • Connect from your account settings",14)); l.addView(tv("Railway • Connect from your account settings",14));
        new AlertDialog.Builder(this).setTitle("Settings").setView(l).setPositiveButton("Save",(d,w)->{prefs.edit().putString("key",key.getText().toString().trim()).apply(); loadModels();}).setNegativeButton("Cancel",null).show();
    }
}