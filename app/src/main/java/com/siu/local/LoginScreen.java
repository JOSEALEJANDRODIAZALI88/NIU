package com.siu.local;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.Locale;

/** Live controls positioned against the 589 x 1280 login reference. */
final class LoginScreen extends FrameLayout {
    final MainActivity activity;
    final EditText password, username;
    final TextView welcome, otherAccount;
    final Typeface handwriting;
    final ReferenceLayout content;
    final View loading;
    AlertDialog alert;
    boolean anotherAccount;

    LoginScreen(MainActivity activity, boolean anotherAccount) {
        super(activity);this.activity=activity;this.anotherAccount=anotherAccount;
        setFocusableInTouchMode(true);
        content=new ReferenceLayout(activity);
        ImageView photo=activity.artwork("univalle_login_imagen");
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(photo,new LayoutParams(-1,content.fullHeight));
        View tint=new View(activity);tint.setBackgroundColor(0xE7130107);addView(tint,new LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);addView(scroll,new LayoutParams(-1,-1));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        handwriting=Typeface.createFromAsset(activity.getAssets(),"fonts/RookieSpanish.ttf");
        ImageView logo=activity.artwork("logo");logo.setContentDescription("Universidad del Valle, Bolivia");
        content.place(logo,209,79,171,171);
        TextView siu=label("S I U",56,Typeface.create("sans-serif",Typeface.BOLD));
        content.place(siu,120,267,349,70);
        welcome=label("¡BIENVENID@ "+firstName()+"!",30,Typeface.createFromAsset(activity.getAssets(),"fonts/Kontana.ttf"));
        content.place(welcome,35,526,519,55);
        username=field("USUARIO",false);username.setContentDescription("Usuario");
        content.place(username,53,524,483,60);
        FrameLayout passwordRow=new FrameLayout(activity);
        password=field("CONTRASEÑA",true);password.setContentDescription("Contraseña");
        passwordRow.addView(password,new LayoutParams(-1,-1));
        TextView clear=label("×",35,Typeface.DEFAULT);clear.setContentDescription("Borrar contraseña");
        LayoutParams cp=new LayoutParams(activity.dp(44),-1,Gravity.RIGHT);
        passwordRow.addView(clear,cp);clear.setVisibility(GONE);
        clear.setOnClickListener(v->password.setText(""));
        password.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){clear.setVisibility(count>0?VISIBLE:GONE);}
            public void afterTextChanged(Editable e){}
        });
        content.place(passwordRow,53,623,483,62);
        TextView submit=label("INICIAR SESIÓN",20,handwriting);submit.setBackground(activity.shape(MainActivity.BRAND,60));
        submit.setContentDescription("Iniciar sesión");submit.setOnClickListener(v->submit());
        content.place(submit,68,748,453,75);
        otherAccount=label("",20,handwriting);otherAccount.setPaintFlags(otherAccount.getPaintFlags()|Paint.UNDERLINE_TEXT_FLAG);
        otherAccount.setOnClickListener(v->switchAccount(!this.anotherAccount));
        content.place(otherAccount,40,878,509,58);
        TextView privacy=label("Política de privacidad",21,Typeface.DEFAULT);
        privacy.setPaintFlags(privacy.getPaintFlags()|Paint.UNDERLINE_TEXT_FLAG);
        privacy.setOnClickListener(v->activity.openPrivacyPolicy());
        content.place(privacy,100,1185,389,55);
        FrameLayout overlay=new FrameLayout(activity);overlay.setBackgroundColor(0x66000000);
        overlay.setClickable(true);overlay.setFocusable(true);
        LinearLayout panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER);
        panel.setBackground(activity.shape(0xDD080306,25));
        ProgressBar spinner=new ProgressBar(activity);spinner.setIndeterminateTintList(ColorStateList.valueOf(MainActivity.BRAND));
        panel.addView(spinner,new LinearLayout.LayoutParams(activity.dp(42),activity.dp(42)));
        TextView entering=activity.text("Ingresando",14,Color.WHITE,false);entering.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.topMargin=activity.dp(8);panel.addView(entering,ep);
        LayoutParams lp=new LayoutParams(activity.dp(104),activity.dp(100),Gravity.CENTER);lp.topMargin=activity.dp(16);
        overlay.addView(panel,lp);
        loading=overlay;addView(loading,new LayoutParams(-1,-1));loading.setVisibility(GONE);
        updateAccount();requestFocus();
    }

    String firstName(){String name=activity.store.firstName();return name.isEmpty()?"USUARIO":name.toUpperCase(Locale.ROOT);}
    TextView label(String value,int size,Typeface font){
        TextView view=new TextView(getContext());view.setText(value);view.setTextColor(0xFFEFEFEF);
        view.setGravity(Gravity.CENTER);view.setIncludeFontPadding(false);view.setTypeface(font);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX,size*getResources().getDisplayMetrics().widthPixels/589f);
        view.setFocusable(true);return view;
    }
    EditText field(String hint,boolean secret){
        EditText field=new EditText(getContext());field.setSingleLine(true);field.setHint(hint);
        field.setGravity(Gravity.CENTER);field.setTextColor(0xFFEFEFEF);field.setHintTextColor(0xFFCCCCCC);
        field.setInputType(secret?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        field.setTypeface(handwriting);field.setTextSize(TypedValue.COMPLEX_UNIT_PX,21*getResources().getDisplayMetrics().widthPixels/589f);
        field.setPadding(activity.dp(32),0,activity.dp(32),0);
        field.setBackgroundTintList(new ColorStateList(new int[][]{{android.R.attr.state_focused},{}},new int[]{0xFFBBBBBB,0xFF16070C}));
        field.setImeOptions(secret?EditorInfo.IME_ACTION_DONE:EditorInfo.IME_ACTION_NEXT);
        field.setSaveEnabled(false);field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        if(secret)field.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_DONE){
                if(event==null||event.getAction()==android.view.KeyEvent.ACTION_DOWN)submit();
                return true;
            }return false;
        });
        return field;
    }
    void updateAccount(){
        welcome.setVisibility(anotherAccount?GONE:VISIBLE);username.setVisibility(anotherAccount?VISIBLE:GONE);
        otherAccount.setText(anotherAccount?"VOLVER A "+firstName():"INICIAR SESIÓN EN OTRA CUENTA");
    }
    void switchAccount(boolean another){
        if(activity.busy)return;activity.biometrics.cancel();anotherAccount=another;activity.loginAnotherAccount=another;
        password.setText("");username.setText("");updateAccount();activity.hideKeyboard();requestFocus();
    }
    void submit(){activity.loginWithPassword(password.getText().toString(),anotherAccount?username.getText().toString():null);}
    void showLoading(){loading.setVisibility(VISIBLE);loading.announceForAccessibility("Ingresando");}
    void showError(String message){
        if(alert!=null&&alert.isShowing())return;
        float scale=getResources().getDisplayMetrics().widthPixels/589f;
        LinearLayout words=new LinearLayout(activity);words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Math.round(36*scale),Math.round(30*scale),Math.round(24*scale),Math.round(24*scale));
        TextView title=label("Atención",25,Typeface.DEFAULT);title.setGravity(Gravity.LEFT);title.setTextColor(0xFFDDDDDD);
        words.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView body=label(message,22,Typeface.DEFAULT);body.setGravity(Gravity.LEFT);body.setTextColor(0xFFBBBBBB);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=Math.round(15*scale);words.addView(body,mp);
        alert=new AlertDialog.Builder(activity).setPositiveButton("Aceptar",null).create();alert.setView(words,0,0,0,0);
        alert.setOnShowListener(d->{
            alert.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF424242));
            android.view.WindowManager.LayoutParams params=alert.getWindow().getAttributes();
            params.width=Math.round(511*scale);params.height=Math.round(224*scale);params.y=Math.round(24*scale);params.dimAmount=.2f;
            alert.getWindow().setAttributes(params);
            Button accept=alert.getButton(AlertDialog.BUTTON_POSITIVE);
            accept.setAllCaps(false);accept.setTextColor(0xFFEFEFEF);accept.setLetterSpacing(.08f);
            accept.setTextSize(TypedValue.COMPLEX_UNIT_PX,22*scale);
            accept.setTranslationX(12*scale);accept.setTranslationY(14*scale);
            ((View)accept.getParent()).setBackgroundColor(0xFF424242);
        });alert.show();
    }
    void dispose(){if(alert!=null)alert.dismiss();}

    static final class ReferenceLayout extends android.view.ViewGroup {
        final int fullHeight;
        ReferenceLayout(Context context){
            super(context);DisplayMetrics metrics=new DisplayMetrics();
            ((MainActivity)context).getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            fullHeight=Math.max(metrics.heightPixels,(int)(metrics.density*640));
        }
        void place(View view,int x,int y,int width,int height){addView(view,new Position(x,y,width,height));}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec),height=Math.max(fullHeight,MeasureSpec.getSize(heightSpec));
            float scale=width/589f;
            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);Position p=(Position)child.getLayoutParams();
                child.measure(MeasureSpec.makeMeasureSpec(Math.round(p.width*scale),MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.max(Math.round(p.height*height/1280f),Math.round(36*getResources().getDisplayMetrics().density)),MeasureSpec.EXACTLY));
            }setMeasuredDimension(width,height);
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            float scale=getWidth()/589f;
            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);Position p=(Position)child.getLayoutParams();
                int x=Math.round(p.x*scale),y=Math.round(p.y*getHeight()/1280f);
                child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());
            }
        }
        static final class Position extends LayoutParams {
            final int x,y;
            Position(int x,int y,int w,int h){super(w,h);this.x=x;this.y=y;}
        }
    }
}
