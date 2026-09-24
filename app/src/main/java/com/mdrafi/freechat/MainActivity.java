package com.mdrafi.freechat;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {
    LinearLayout root, messages;
    Button modelButton;
    EditText input;
    TextView status;
    ArrayList<String> models=new ArrayList<>();
    ArrayList<String> modelIds=new ArrayList<>();
    SharedPreferences prefs;
    int selectedModel=0;
    final String BASE="https://vyceai.com";

    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,int size){
        TextView t=new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(Color.WHITE);
        t.setPadding(dp(12),dp(6),dp(12),dp(6));
        return t;
    }
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
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18,18,18));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=tv("Free Chat",21);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));

        Button newChat=btn("＋ New");
        top.addView(newChat,new LinearLayout.LayoutParams(dp(86),dp(56)));
        Button settings=btn("⚙");
        top.addView(settings,new LinearLayout.LayoutParams(dp(54),dp(56)));
        root.addView(top);

        LinearLayout modelRow=new LinearLayout(this);
        modelRow.setPadding(dp(10),dp(4),dp(10),dp(2));
        modelRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView modelLabel=tv("MODEL",11);
        modelLabel.setTextColor(Color.LTGRAY);
        modelRow.addView(modelLabel,new LinearLayout.LayoutParams(dp(58),dp(52)));

        modelButton=btn("Select model");
        modelButton.setTextSize(14);
        modelRow.addView(modelButton,new LinearLayout.LayoutParams(0,dp(52),1));
        modelButton.setOnClickListener(v->showModelPicker());
        root.addView(modelRow);

        status=tv("Loading models…",12);
        status.setTextColor(Color.LTGRAY);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(32)));

        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true);
        messages=new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(10),dp(6),dp(10),dp(10));
        sv.addView(messages);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout bar=new LinearLayout(this);
        bar.setPadding(dp(8),dp(5),dp(8),dp(8));
        input=new EditText(this);
        input.setHint("Ask anything…");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bar.addView(input,new LinearLayout.LayoutParams(0,dp(58),1));
        Button send=btn("Send");
        bar.addView(send,new LinearLayout.LayoutParams(dp(82),dp(58)));
        root.addView(bar);

        setContentView(root);
        newChat.setOnClickListener(v->messages.removeAllViews());
        settings.setOnClickListener(v->showSettings());
        send.setOnClickListener(v->send());
    }

    void showModelPicker(){
        if(models.isEmpty()){
            Toast.makeText(this,"No models loaded yet",Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names=models.toArray(new String[0]);
        int checked=Math.min(selectedModel,names.length-1);
        new AlertDialog.Builder(this)
            .setTitle("Choose AI model")
            .setSingleChoiceItems(names,checked,(dialog,which)->{
                selectedModel=which;
                modelButton.setText("✓ "+models.get(which));
                dialog.dismiss();
            })
            .setNegativeButton("Cancel",null)
            .show();
    }

    void loadModels(){
        new Thread(()->{
            try{
                String key=prefs.getString("key","");
                if(key.isEmpty()){
                    runOnUiThread(()->status.setText("Add your Vyce API key in ⚙ Settings"));
                    return;
                }
                HttpsURLConnection c=(HttpsURLConnection)new URL(BASE+"/v1/models").openConnection();
                c.setRequestProperty("Authorization","Bearer "+key);
                c.setConnectTimeout(15000); c.setReadTimeout(15000);
                String body=read(c);
                JSONObject o=new JSONObject(body);
                JSONArray a=o.getJSONArray("data");
                models.clear(); modelIds.clear();
                for(int i=0;i<a.length();i++){
                    JSONObject m=a.getJSONObject(i);
                    String id=m.optString("id");
                    if(!id.isEmpty()){modelIds.add(id); models.add(id);}
                }
                runOnUiThread(()->{
                    selectedModel=0;
                    if(!models.isEmpty()) modelButton.setText("✓ "+models.get(0));
                    status.setText(models.size()+" models available • Tap MODEL to switch");
                });
            }catch(Exception e){
                runOnUiThread(()->status.setText("Model load failed"));
            }
        }).start();
    }

    void send(){
        String q=input.getText().toString().trim();
        if(q.isEmpty())return;
        String key=prefs.getString("key","");
        if(key.isEmpty()){showSettings(); return;}
        if(modelIds.isEmpty()){Toast.makeText(this,"Please load a model first",Toast.LENGTH_SHORT).show(); return;}
        addMessage("You",q);
        input.setText("");
        int pos=Math.min(selectedModel,modelIds.size()-1);
        String model=modelIds.get(pos);
        new Thread(()->{
            try{
                HttpsURLConnection c=(HttpsURLConnection)new URL(BASE+"/v1/chat/completions").openConnection();
                c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setRequestProperty("Authorization","Bearer "+key);
                c.setRequestProperty("Content-Type","application/json");
                JSONArray msgs=new JSONArray();
                JSONObject msg=new JSONObject();
                msg.put("role","user"); msg.put("content",q); msgs.put(msg);
                JSONObject req=new JSONObject();
                req.put("model",model); req.put("messages",msgs); req.put("stream",false);
                OutputStream os=c.getOutputStream();
                os.write(req.toString().getBytes("UTF-8")); os.close();
                String body=read(c);
                JSONObject o=new JSONObject(body);
                String ans=o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","No response");
                runOnUiThread(()->addMessage(model,ans));
            }catch(Exception e){
                runOnUiThread(()->addMessage("Error",e.getMessage()==null?"Request failed":e.getMessage()));
            }
        }).start();
    }

    String read(HttpsURLConnection c)throws Exception{
        InputStream is=c.getResponseCode()>=400?c.getErrorStream():c.getInputStream();
        BufferedReader r=new BufferedReader(new InputStreamReader(is));
        StringBuilder s=new StringBuilder(); String l;
        while((l=r.readLine())!=null)s.append(l);
        r.close(); return s.toString();
    }

    void addMessage(String who,String text){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),dp(7),dp(8),dp(7));
        TextView h=tv(who,12);
        h.setTextColor(Color.LTGRAY);
        TextView b=tv(text,16);
        b.setTextIsSelectable(true);
        box.addView(h);
        box.addView(b);
        Button copy=btn("Copy");
        copy.setOnClickListener(v->{
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("response",text));
            Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();
        });
        box.addView(copy,new LinearLayout.LayoutParams(-2,dp(40)));
        messages.addView(box);
    }

    void showSettings(){
        LinearLayout l=new LinearLayout(this);
        l.setPadding(dp(20),dp(10),dp(20),dp(10));
        l.setOrientation(LinearLayout.VERTICAL);
        EditText key=new EditText(this);
        key.setHint("Vyce API key");
        key.setText(prefs.getString("key",""));
        key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        l.addView(key);
        l.addView(tv("Plugins",18));
        l.addView(tv("GitHub • Connect from your account settings",14));
        l.addView(tv("Railway • Connect from your account settings",14));
        new AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(l)
            .setPositiveButton("Save",(d,w)->{
                prefs.edit().putString("key",key.getText().toString().trim()).apply();
                loadModels();
            })
            .setNegativeButton("Cancel",null).show();
    }
}