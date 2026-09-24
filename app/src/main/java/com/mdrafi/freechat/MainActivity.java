package com.mdrafi.freechat;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root, messages;
    EditText input;
    TextView modelButton, status, welcomeTitle, welcomeSub;
    ArrayList<String> models=new ArrayList<>(), modelIds=new ArrayList<>();
    ArrayList<String> providers=new ArrayList<>();
    SharedPreferences prefs;
    int selectedModel=0, activeProvider=0;
    boolean dark=true;
    String apiBase="https://vyceai.com", modelsPath="/v1/models", chatPath="/v1/chat/completions";

    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    int bg(){return Color.parseColor(dark?"#171717":"#F7F7F5");}
    int fg(){return Color.parseColor(dark?"#F4F4F2":"#242424");}
    int muted(){return Color.parseColor(dark?"#A5A5A0":"#6B6B66");}
    int panel(){return Color.parseColor(dark?"#232323":"#FFFFFF");}
    int border(){return Color.parseColor(dark?"#343434":"#E4E4E0");}
    TextView text(String s,float size){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(fg());
        t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    GradientDrawable rounded(int color,float radius){
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); g.setStroke(dp(1),border()); return g;
    }
    TextView action(String label){
        TextView t=text(label,14); t.setGravity(Gravity.CENTER); t.setPadding(dp(10),0,dp(10),0);
        t.setBackground(rounded(panel(),12)); return t;
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("freechat",0);
        dark=prefs.getBoolean("dark",true);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        build(); applyBars(); applyInsets(); loadModels();
    }
    void applyBars(){
        getWindow().setStatusBarColor(bg()); getWindow().setNavigationBarColor(bg());
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }
    void applyInsets(){
        root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());
            v.setPadding(0,bars.top,0,bars.bottom); return insets;
        }); root.requestApplyInsets();
    }
    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg());
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(10),dp(6),dp(10),dp(6));
        TextView menu=action("☰"); top.addView(menu,new LinearLayout.LayoutParams(dp(46),dp(44)));
        TextView title=text("Free Chat",18); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setGravity(Gravity.CENTER);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView newChat=action("＋"); top.addView(newChat,new LinearLayout.LayoutParams(dp(46),dp(44)));
        TextView settings=action("⚙"); top.addView(settings,new LinearLayout.LayoutParams(dp(46),dp(44)));
        root.addView(top);

        LinearLayout modelRow=new LinearLayout(this); modelRow.setGravity(Gravity.CENTER_VERTICAL); modelRow.setPadding(dp(16),dp(4),dp(16),dp(8));
        modelButton=text("Select model  ▾",13); modelButton.setGravity(Gravity.CENTER);
        modelButton.setPadding(dp(14),0,dp(14),0); modelButton.setBackground(rounded(panel(),18));
        modelRow.addView(modelButton,new LinearLayout.LayoutParams(-2,dp(38))); modelButton.setOnClickListener(v->showModelPicker());
        status=text("Connecting…",11); status.setTextColor(muted()); status.setGravity(Gravity.CENTER_VERTICAL);
        modelRow.addView(status,new LinearLayout.LayoutParams(0,dp(38),1)); root.addView(modelRow);

        ScrollView sv=new ScrollView(this); sv.setFillViewport(true);
        messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(18),dp(8),dp(18),dp(18)); sv.addView(messages);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        showWelcome();

        LinearLayout composerWrap=new LinearLayout(this); composerWrap.setPadding(dp(12),dp(8),dp(12),dp(12));
        LinearLayout composer=new LinearLayout(this); composer.setOrientation(LinearLayout.VERTICAL); composer.setPadding(dp(12),dp(8),dp(8),dp(8)); composer.setBackground(rounded(panel(),18));
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView plus=action("＋"); row.addView(plus,new LinearLayout.LayoutParams(dp(42),dp(42)));
        input=new EditText(this); input.setHint("Message Free Chat…"); input.setHintTextColor(muted()); input.setTextColor(fg());
        input.setTextSize(16); input.setSingleLine(false); input.setMaxLines(5); input.setPadding(dp(10),0,dp(8),0);
        input.setBackgroundColor(Color.TRANSPARENT); input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        row.addView(input,new LinearLayout.LayoutParams(0,dp(50),1));
        TextView send=action("↑"); row.addView(send,new LinearLayout.LayoutParams(dp(44),dp(42)));
        composer.addView(row); composerWrap.addView(composer,new LinearLayout.LayoutParams(-1,-2)); root.addView(composerWrap);

        setContentView(root);
        send.setOnClickListener(v->send());
        newChat.setOnClickListener(v->{messages.removeAllViews(); showWelcome();});
        settings.setOnClickListener(v->showSettings());
        menu.setOnClickListener(v->showAbout());
        plus.setOnClickListener(v->Toast.makeText(this,"Attachments can be added when the selected model supports them.",Toast.LENGTH_SHORT).show());
    }
    void showWelcome(){
        welcomeTitle=text("How can I help you today?",25); welcomeTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        welcomeTitle.setGravity(Gravity.CENTER); welcomeTitle.setPadding(0,dp(80),0,dp(8));
        messages.addView(welcomeTitle,new LinearLayout.LayoutParams(-1,-2));
        welcomeSub=text("Ask anything, brainstorm, write, code, or learn.",14); welcomeSub.setTextColor(muted()); welcomeSub.setGravity(Gravity.CENTER);
        messages.addView(welcomeSub,new LinearLayout.LayoutParams(-1,dp(42)));
    }
    void showModelPicker(){
        if(models.isEmpty()){Toast.makeText(this,"No models loaded yet",Toast.LENGTH_SHORT).show();return;}
        String[] names=models.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Choose model").setSingleChoiceItems(names,Math.min(selectedModel,names.length-1),(d,w)->{
            selectedModel=w; modelButton.setText(models.get(w)+"  ▾"); d.dismiss();
        }).setNegativeButton("Cancel",null).show();
    }
    void loadModels(){
        loadProvider();
        new Thread(()->{try{
            String key=prefs.getString("provider_key_")+activeProvider,"");
            if(key.isEmpty()){runOnUiThread(()->status.setText("Add API key in ⚙ → API Manager"));return;}
            if(key.isEmpty()){runOnUiThread(()->status.setText("Add API key in ⚙"));return;}
            HttpsURLConnection c=(HttpsURLConnection)new URL(apiBase+modelsPath).openConnection();
            c.setRequestProperty("Authorization","Bearer "+key); c.setConnectTimeout(15000); c.setReadTimeout(15000);
            JSONObject o=new JSONObject(read(c)); JSONArray a=o.getJSONArray("data");
            models.clear(); modelIds.clear();
            for(int i=0;i<a.length();i++){String id=a.getJSONObject(i).optString("id");if(!id.isEmpty()){modelIds.add(id);models.add(id);}}
            runOnUiThread(()->{selectedModel=0;if(!models.isEmpty())modelButton.setText(models.get(0)+"  ▾");status.setText(models.size()+" models");});
        }catch(Exception e){runOnUiThread(()->status.setText("Model connection failed"));}}).start();
    }
    void send(){
        String q=input.getText().toString().trim(); if(q.isEmpty())return;
        loadProvider();
        String key=prefs.getString("provider_key_"+activeProvider,"");
        if(key.isEmpty()){showSettings();return;}
        if(modelIds.isEmpty()){Toast.makeText(this,"Please load a model first",Toast.LENGTH_SHORT).show();return;}
        if(welcomeTitle!=null){messages.removeView(welcomeTitle);messages.removeView(welcomeSub);welcomeTitle=null;welcomeSub=null;}
        addMessage("You",q,true); input.setText("");
        final int pos=Math.min(selectedModel,modelIds.size()-1); final String model=modelIds.get(pos);
        TextView thinking=text(model+"  •  Thinking…",13); thinking.setTextColor(muted()); thinking.setPadding(0,dp(16),0,dp(16));
        messages.addView(thinking); scrollBottom();
        new Thread(()->{try{
            HttpsURLConnection c=(HttpsURLConnection)new URL(apiBase+chatPath).openConnection();
            c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json");
            JSONArray arr=new JSONArray();JSONObject msg=new JSONObject();msg.put("role","user");msg.put("content",q);arr.put(msg);
            JSONObject req=new JSONObject();req.put("model",model);req.put("messages",arr);req.put("stream",false);
            OutputStream os=c.getOutputStream();os.write(req.toString().getBytes("UTF-8"));os.close();
            JSONObject o=new JSONObject(read(c));String ans=o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","No response");
            runOnUiThread(()->{messages.removeView(thinking);addMessage(model,ans,false);});
        }catch(Exception e){runOnUiThread(()->{messages.removeView(thinking);addMessage("Error",e.getMessage()==null?"Request failed":e.getMessage(),false);});}}).start();
    }
    void scrollBottom(){messages.post(()->{ViewParent p=messages.getParent();if(p instanceof ScrollView)((ScrollView)p).fullScroll(View.FOCUS_DOWN);});}
    String read(HttpsURLConnection c)throws Exception{
        InputStream is=c.getResponseCode()>=400?c.getErrorStream():c.getInputStream();BufferedReader r=new BufferedReader(new InputStreamReader(is));
        StringBuilder s=new StringBuilder();String l;while((l=r.readLine())!=null)s.append(l);r.close();return s.toString();
    }
    void addMessage(String who,String value,boolean user){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,dp(10),0,dp(10));
        TextView h=text(who,12);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);h.setTextColor(muted());box.addView(h);
        TextView b=text(value,16);b.setGravity(Gravity.TOP);b.setTextIsSelectable(true);b.setPadding(0,dp(5),0,dp(5));box.addView(b);
        if(!user){LinearLayout acts=new LinearLayout(this);TextView copy=action("Copy");copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("response",value));Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();});acts.addView(copy,new LinearLayout.LayoutParams(dp(76),dp(36)));box.addView(acts);}
        messages.addView(box);scrollBottom();
    }
    void loadProvider(){
        int count=prefs.getInt("provider_count",1);
        if(count<1)count=1;
        activeProvider=Math.max(0,Math.min(activeProvider,count-1));
        apiBase=prefs.getString("provider_base_"+activeProvider,"https://vyceai.com");
        modelsPath=prefs.getString("provider_models_"+activeProvider,"/v1/models");
        chatPath=prefs.getString("provider_chat_"+activeProvider,"/v1/chat/completions");
    }
    void showApiManager(){
        loadProvider();
        ArrayList<String> names=new ArrayList<>();
        int count=prefs.getInt("provider_count",1);
        for(int i=0;i<count;i++) names.add(prefs.getString("provider_name_"+i,"Provider "+(i+1)));
        String[] items=new String[names.size()+1];
        for(int i=0;i<names.size();i++) items[i]=names.get(i)+(i==activeProvider?"  ✓":"");
        items[names.size()]="＋ Add API";
        new AlertDialog.Builder(this).setTitle("API Manager")
          .setItems(items,(d,w)->{
              if(w==names.size()) editProvider(-1);
              else { activeProvider=w; loadProvider(); loadModels(); Toast.makeText(this,"API: "+names.get(w),Toast.LENGTH_SHORT).show(); }
          }).setNegativeButton("Close",null)
          .setNeutralButton("Manage",null).create().show();
    }
    void editProvider(int index){
        boolean isNew=index<0; if(isNew) index=prefs.getInt("provider_count",1);
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(18),0,dp(18),0);
        EditText name=new EditText(this); name.setHint("API name"); name.setSingleLine(true);
        EditText base=new EditText(this); base.setHint("Base URL e.g. https://example.com"); base.setSingleLine(true);
        EditText key=new EditText(this); key.setHint("API key / token"); key.setSingleLine(true); key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText mp=new EditText(this); mp.setHint("Models endpoint"); mp.setSingleLine(true); mp.setText("/v1/models");
        EditText cp=new EditText(this); cp.setHint("Chat endpoint"); cp.setSingleLine(true); cp.setText("/v1/chat/completions");
        if(!isNew){name.setText(prefs.getString("provider_name_"+index,""));base.setText(prefs.getString("provider_base_"+index,""));key.setText(prefs.getString("provider_key_"+index,""));mp.setText(prefs.getString("provider_models_"+index,"/v1/models"));cp.setText(prefs.getString("provider_chat_"+index,"/v1/chat/completions"));}
        l.addView(name);l.addView(base);l.addView(key);l.addView(mp);l.addView(cp);
        new AlertDialog.Builder(this).setTitle(isNew?"Add API":"Edit API").setView(l).setPositiveButton("Save",(d,w)->{
            prefs.edit().putString("provider_name_"+index,name.getText().toString().trim().isEmpty()?"API "+(index+1):name.getText().toString().trim())
              .putString("provider_base_"+index,base.getText().toString().trim().replaceAll("/$",""))
              .putString("provider_key_"+index,key.getText().toString().trim())
              .putString("provider_models_"+index,mp.getText().toString().trim())
              .putString("provider_chat_"+index,cp.getText().toString().trim())
              .putInt("provider_count",Math.max(prefs.getInt("provider_count",1),index+1)).apply();
            activeProvider=index; loadProvider(); loadModels();
        }).setNegativeButton("Cancel",null).show();
    }
    void showSettings(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),0,dp(18),0);
        TextView api=text("API Manager",15); api.setTextColor(fg()); api.setPadding(0,dp(8),0,dp(8)); l.addView(api); api.setOnClickListener(v->showApiManager());
        EditText key=new EditText(this);key.setHint("Legacy Vyce API key (optional)");key.setText(prefs.getString("key",""));key.setSingleLine(true);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);l.addView(key);
        TextView theme=text("Appearance",14);theme.setTextColor(muted());theme.setPadding(0,dp(18),0,dp(6));l.addView(theme);
        Switch sw=new Switch(this);sw.setText("Dark mode");sw.setTextColor(fg());sw.setChecked(dark);l.addView(sw);
        new AlertDialog.Builder(this).setTitle("Settings").setView(l).setPositiveButton("Save",(d,w)->{prefs.edit().putString("key",key.getText().toString().trim()).putBoolean("dark",sw.isChecked()).apply();dark=sw.isChecked();build();applyBars();applyInsets();loadModels();}).setNegativeButton("Cancel",null).show();
    }
    void showAbout(){new AlertDialog.Builder(this).setTitle("Free Chat").setMessage("A clean AI chat client powered by your Vyce API key.\n\nTip: use the model chip at the top to switch models.").setPositiveButton("OK",null).show();}
}
