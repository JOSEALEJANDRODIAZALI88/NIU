package com.siu.local;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.*;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.util.zip.*;

/** End-to-end smoke checks for navigation, local editing, images and backup round trips. */
public class SmokeInstrumentation extends Instrumentation {
    boolean screenshots;
    @Override public void onCreate(Bundle args) {super.onCreate(args);screenshots=args!=null&&"true".equals(args.getString("screenshots"));start();}
    int checks;
    void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    @Override public void onStart(){
        Bundle result=new Bundle();MainActivity activity=null;LocalStore previous=null;
        try{
            Context context=getTargetContext();
            File sandbox=new File(context.getCacheDir(),"smoke-store");sandbox.mkdirs();
            Context isolated=new ContextWrapper(context){@Override public File getFilesDir(){return sandbox;}};
            File data=new File(sandbox,"content.json");data.delete();
            LocalStore local=new LocalStore(isolated);
            checkPhysicalProfile(local,isolated);
            LocalStore.put(local.obj("perfil"),"nombre","PRUEBA DE PERSISTENCIA");local.save();
            check(new LocalStore(isolated).obj("perfil").getString("nombre").equals("PRUEBA DE PERSISTENCIA"),"Profile persistence");
            Bitmap picture=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);picture.eraseColor(Color.MAGENTA);
            File imageFile=new File(sandbox,"source.png");try(OutputStream out=new FileOutputStream(imageFile)){picture.compress(Bitmap.CompressFormat.PNG,100,out);}
            String imageName=local.importImage(Uri.fromFile(imageFile));LocalStore.put(local.obj("perfil"),"foto",imageName);local.save();
            check(local.image(imageName,null).getPixel(0,0)==Color.MAGENTA,"Image import and reading");
            File zip=new File(sandbox,"backup.zip");local.exportZip(Uri.fromFile(zip));
            LocalStore.put(local.obj("perfil"),"nombre","CAMBIADO");local.save();new File(sandbox,imageName).delete();
            local.importZip(Uri.fromFile(zip));
            check(local.obj("perfil").getString("nombre").equals("PRUEBA DE PERSISTENCIA"),"Backup restores data");
            check(local.image(imageName,null).getPixel(0,0)==Color.MAGENTA,"Backup restores images");
            File invalid=new File(sandbox,"invalid.zip");try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(invalid))){out.putNextEntry(new ZipEntry("../escape.png"));out.write(new byte[]{1});out.closeEntry();}
            boolean rejected=false;try{local.importZip(Uri.fromFile(invalid));}catch(IOException expected){rejected=true;}
            check(rejected,"Reject path traversal in backup");
            check(local.obj("perfil").getString("nombre").equals("PRUEBA DE PERSISTENCIA"),"Failed import keeps data");

            Intent intent=new Intent(context,MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity=(MainActivity)startActivitySync(intent);checkLogin(activity);activity=checkLockedRecreation(activity);activity=checkCarnetRecreation(activity);MainActivity a=activity;previous=a.store;
            runOnMainSync(()->{a.store=local;a.history.clear();});
            checkPhysicalCardUi(a,local);
            checkProfileEmailLayout(a,local);
            String[] routes={"home","drawer","perfil","carnet","ajustes","Social","Materias","Académica","Recursos","Tip's","contactos:0","contactos:1","contactos:2","contactos:3","contactos:4","lugares:0","consultas","consulta:0","nuevaConsulta:0","sugerencias","sugerencia:0","documentos:0","documentos:1","horarios:0","horarios:1","horarios:5","notas","pagos","costos","tramites","tipos","detalle:0","biblioteca","descuentos","correo","proyectos","bancos:0","bancos:1","ayuda","ayudaDetalle:0","ayudaDetalle:1","ayudaDetalle:2","materia:0","editor:","editor:perfil","editor:materias","editor:materias/0","editor:horarios","editor:tiposTramites/0/requisitos","acceso"};
            for(String route:routes){runOnMainSync(()->{a.route=route;a.render();check(a.frame.getChildCount()>0,"Render "+route);});waitForIdleSync();}
            passwordLogin(a);
            runOnMainSync(()->{a.selectedGestion=local.arr("gestiones").optString(0);a.route="notas";a.render();check(a.body.getChildCount()>=2,"Grades selector and results");a.route="editor:perfil";a.render();EditText field=findEdit(a.root);check(field!=null,"Profile editor has editable fields");field.setText("EDITADO EN UI");a.setPath("materias/0/parcial1","99,0");
                a.route="editor:notasAnteriores/Gestión 1~12026";a.render();check(a.resolve("notasAnteriores/Gestión 1~12026") instanceof JSONArray,"Historical semester path supports slash");
            });
            check(new LocalStore(isolated).obj("perfil").getString("nombre").equals("EDITADO EN UI"),"Editor saves profile");
            check(new LocalStore(isolated).arr("materias").getJSONObject(0).getString("parcial1").equals("99,0"),"Editor saves grades");
            runOnMainSync(()->{LocalStore.put(local.obj("ajustes"),"tema","Claro");a.route="home";a.render();check(a.bg!=Color.BLACK,"Light theme");LocalStore.put(local.obj("ajustes"),"tema","Oscuro");});
            checkCarnet(a,local,imageName);
            check(local.arr("pagos").length()==6,"Reference payment plan");
            check(local.image("","artwork/logo_carnet.png").getWidth()==886,"Original high-resolution watermark");
            check(local.image("","portrait_carnet.png").getWidth()==408,"Carnet uses the physical-card portrait");
            check(local.image("","seal.jpg").getWidth()==472,"Legacy seal fallback uses original artwork");
            check(SiuIcon.codepoint("Social")==0xf500,"Original two-person social glyph");
            check(context.getPackageManager().getApplicationIcon(context.getPackageName()) instanceof android.graphics.drawable.AdaptiveIconDrawable,"Adaptive launcher icon");
            check(context.getPackageManager().getApplicationLabel(context.getApplicationInfo()).toString().equals("SIU"),"Launcher label is SIU without Local");
            runOnMainSync(()->{LocalStore.put(local.obj("ajustes"),"cuadricula",true);a.route="home";a.render();check(findImageResource(a.root)==6,"Grid contains five original PNG icons and watermark");a.route="perfil";a.render();check(findDescription(a.root,"Bandera de Bolivia")!=null,"Profile uses original flag, not emoji");a.route="carnet";a.render();check(findDescription(a.root,"Bandera de Bolivia")!=null,"Carnet flag");});
            runOnMainSync(()->{
                LocalStore.put(local.obj("ajustes"),"cuadricula",false);a.route="home";a.render();
                findDescription(a.root,"Abrir menú").performClick();check(a.route.equals("drawer"),"Open drawer from home");
                ((android.view.View)findText(a.frame,"Perfil de usuario").getParent()).performClick();check(a.route.equals("perfil"),"Drawer opens profile");
                findDescription(a.frame,"Editar perfil").performClick();check(a.route.equals("editor:perfil"),"Profile edit button opens editor");a.back();check(a.route.equals("perfil"),"Back returns to profile");
                a.route="home";a.history.clear();a.render();findDescription(a.root,"Ver cuadrícula").performClick();check(local.obj("ajustes").optBoolean("cuadricula"),"Grid toggle saves preference");
                findDescription(a.root,"Ver lista").performClick();check(!local.obj("ajustes").optBoolean("cuadricula"),"List toggle saves preference");
                a.route="ajustes";a.render();findText(a.root,"Claro").performClick();check(a.bg!=Color.BLACK,"Theme radio updates appearance");findText(a.root,"Oscuro").performClick();check(a.bg==Color.BLACK,"Dark theme radio updates appearance");
            });
            if(screenshots) captureScreens(a,local);
            result.putString("stream","\nOK: "+checks+" comprobaciones: pantallas, persistencia, edición, imágenes, QR y copia ZIP.\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","\nERROR: "+e+"\n"+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
        finally{if(activity!=null&&previous!=null){MainActivity a=activity;LocalStore original=previous;runOnMainSync(()->{a.store=original;a.route="home";a.selectedGestion="";a.history.clear();a.render();});}}
    }
    void checkPhysicalProfile(LocalStore local,Context isolated)throws Exception{
        JSONObject original=new JSONObject(local.data.toString()),p=local.obj("perfil");
        check(p.getString("nombre").equals("JOSÉ ALEJANDRO DIAZ ALI"),"Physical-card full name");
        check(p.getString("correo").equals("daj2033135@est.univalle.edu"),"Physical-card email");
        check(p.getString("sede").equals("LA PAZ")&&p.getString("carrera").equals("ING. BIOMEDICA"),"Physical-card campus and career");
        check(p.getString("qrTexto").equals("DAJ2033135"),"Exact decoded physical-card QR payload");
        check(p.getString("apoderado").equals("FELIX MANUEL")&&p.getString("genero").equals("MASCULINO")&&p.getString("nacimiento").equals("26/03/2003"),"User-supplied guardian, gender and birth date");
        check(local.image("","portrait.jpg").getWidth()==408&&local.image("","portrait_carnet.png").getHeight()==408,"Both portraits use the same square card crop");
        try{
            JSONObject legacy=new JSONObject(original.toString()),old=legacy.getJSONObject("perfil");
            LocalStore.put(old,"nombre","LUZ GABRIELA CASTAÑETA TARQUINO");LocalStore.put(old,"correo","ctl2033013@est.univalle.edu");
            LocalStore.put(old,"qrTexto","SIU-LOCAL:2033013");LocalStore.put(old,"apoderado","JUAN CARLOS");LocalStore.put(old,"genero","FEMENINO");LocalStore.put(old,"nacimiento","2/11/2005");
            LocalStore.put(old,"foto","image_oldportrait.png");LocalStore.put(old,"qrImagen","image_oldqr.png");LocalStore.put(old,"lector",false);
            LocalStore.put(legacy.getJSONObject("ajustes"),"tema","Claro");LocalStore.put(legacy.getJSONArray("materias").getJSONObject(0),"parcial1","99,0");
            LocalStore.put(legacy.getJSONObject("proyectos"),"saludo","¡Hola Luz!");
            local.data=legacy;local.save();LocalStore migrated=new LocalStore(isolated);JSONObject updated=migrated.obj("perfil");
            check(updated.getString("nombre").equals(p.getString("nombre"))&&updated.getString("correo").equals(p.getString("correo")),"Installed legacy identity migrates");
            check(updated.getString("qrTexto").equals("DAJ2033135")&&updated.getString("foto").isEmpty()&&updated.getString("qrImagen").isEmpty(),"Migration replaces legacy portrait and custom QR references");
            check(updated.getString("apoderado").equals("FELIX MANUEL")&&updated.getString("genero").equals("MASCULINO")&&updated.getString("nacimiento").equals("26/03/2003"),"Migration uses user-supplied personal details");
            check(!updated.getBoolean("lector")&&migrated.obj("ajustes").getString("tema").equals("Claro"),"Migration preserves biometric preference and theme");
            check(migrated.arr("materias").getJSONObject(0).getString("parcial1").equals("99,0"),"Migration preserves edited academic content");
            check(migrated.obj("proyectos").getString("saludo").equals("¡Hola José!"),"Migration updates legacy greeting");
            check(new LocalStore(isolated).obj("perfil").getString("nombre").equals(p.getString("nombre")),"Migrated identity persists");
            LocalStore.put(updated,"nacimiento","01/01/2000");migrated.save();
            check(new LocalStore(isolated).obj("perfil").getString("nacimiento").equals("01/01/2000"),"Reopening does not reapply migration to edited card profile");
            LocalStore.put(old,"nombre","PERFIL PERSONALIZADO");local.data=legacy;local.save();
            check(new LocalStore(isolated).obj("perfil").getString("foto").equals("image_oldportrait.png"),"Unrelated personalized identity and photo are preserved");
            LocalStore.put(old,"nombre","LUZ GABRIELA CASTAÑETA TARQUINO");LocalStore.put(old,"foto","");LocalStore.put(old,"qrImagen","");
            File backup=new File(isolated.getFilesDir(),"legacy-card.zip");local.exportZip(Uri.fromFile(backup));
            local.importZip(Uri.fromFile(backup));
            check(local.obj("perfil").getString("qrTexto").equals("DAJ2033135"),"Legacy backup migrates immediately on import");
            check(new LocalStore(isolated).obj("perfil").getString("correo").equals(p.getString("correo")),"Migrated backup persists");
            JSONObject incomplete=new JSONObject(original.toString());incomplete.remove("physical_card_details_version");
            JSONObject details=incomplete.getJSONObject("perfil");
            for(String key:new String[]{"apoderado","genero","nacimiento"})LocalStore.put(details,key,"");
            local.data=incomplete;local.save();LocalStore completed=new LocalStore(isolated);
            check(completed.obj("perfil").getString("apoderado").equals("FELIX MANUEL")&&completed.obj("perfil").getString("genero").equals("MASCULINO")&&completed.obj("perfil").getString("nacimiento").equals("26/03/2003"),"Previously installed physical profile receives missing details");
            for(String key:new String[]{"apoderado","genero","nacimiento"})LocalStore.put(completed.obj("perfil"),key,"");
            completed.save();JSONObject cleared=new LocalStore(isolated).obj("perfil");
            check(cleared.getString("apoderado").isEmpty()&&cleared.getString("genero").isEmpty()&&cleared.getString("nacimiento").isEmpty(),"Details migration runs once and respects later clearing");
            LocalStore.put(details,"nacimiento","01/01/2000");local.data=incomplete;local.save();LocalStore partial=new LocalStore(isolated);
            check(partial.obj("perfil").getString("apoderado").equals("FELIX MANUEL")&&partial.obj("perfil").getString("nacimiento").equals("01/01/2000"),"Missing details are filled without overwriting a customized date");
            LocalStore.put(details,"nacimiento","");local.data=incomplete;
            File incompleteBackup=new File(isolated.getFilesDir(),"incomplete-card.zip");local.exportZip(Uri.fromFile(incompleteBackup));
            local.importZip(Uri.fromFile(incompleteBackup));
            check(local.obj("perfil").getString("apoderado").equals("FELIX MANUEL")&&local.obj("perfil").getString("nacimiento").equals("26/03/2003"),"Older physical-card backup receives missing details immediately");
        }finally{local.data=original;local.save();}
    }
    void checkPhysicalCardUi(MainActivity a,LocalStore local)throws Exception{
        JSONObject previous=local.data;
        JSONObject defaults=new JSONObject(new String(a.readAsset("defaults.json"),java.nio.charset.StandardCharsets.UTF_8));
        try{
            runOnMainSync(()->{
                local.data=defaults;a.logout();
                check(a.loginScreen.firstName().equals("JOSÉ")&&findText(a.frame,"¡BIENVENID@ JOSÉ!")!=null,"Login welcomes physical-card owner");
                a.loginScreen.switchAccount(true);a.loginScreen.username.setText("daj2033135");
            });passwordLogin(a);
            runOnMainSync(()->{
                a.route="carnet";a.carnetUser=false;a.render();
                check(findText(a.root,a.carnetName("JOSÉ ALEJANDRO DIAZ ALI"))!=null,"Carnet shows new owner without redesign");
                check(decodeQr(displayedQr(a)).equals("DAJ2033135"),"Displayed physical-card QR decodes exactly");
                findText(a.root,"Usuario").performClick();
                check(findText(a.root,"JOSÉ ALEJANDRO DIAZ ALI")!=null&&findText(a.root,"daj2033135@est.univalle.edu")!=null,"Usuario uses the same physical-card identity");
                check(findText(a.root,"FELIX MANUEL")!=null&&findText(a.root,"MASCULINO")!=null&&findText(a.root,"26/03/2003")!=null,"Usuario displays the supplied personal details");
                a.route="drawer";a.render();check(findText(a.frame,"JOSÉ ALEJANDRO DIAZ ALI")!=null,"Drawer uses physical-card owner");
                a.route="perfil";a.render();check(findText(a.root,"JOSÉ ALEJANDRO DIAZ ALI")!=null,"Profile uses physical-card owner");
                a.route="correo";a.render();check(findText(a.root,"Cuenta:  daj2033135@est.univalle.edu")!=null,"Email screen uses physical-card email");
                a.route="proyectos";a.render();check(findText(a.root,"¡Hola José!")!=null,"Projects greet physical-card owner");
                LocalStore.put(local.obj("perfil"),"nombre","MARÍA PRUEBA");check(a.projectGreeting().equals("¡Hola María!"),"Default project greeting follows profile edits");
                LocalStore.put(local.obj("proyectos"),"saludo","Bienvenido al proyecto");check(a.projectGreeting().equals("Bienvenido al proyecto"),"Custom project greeting is preserved");
            });
        }finally{runOnMainSync(()->{local.data=previous;a.route="home";a.history.clear();a.carnetUser=false;a.render();});}
    }
    EditText findEdit(android.view.View view){if(view instanceof EditText e)return e;if(view instanceof android.view.ViewGroup g)for(int i=0;i<g.getChildCount();i++){EditText e=findEdit(g.getChildAt(i));if(e!=null)return e;}return null;}
    void checkProfileEmailLayout(MainActivity a,LocalStore local){
        runOnMainSync(()->{
            String original=local.obj("perfil").optString("correo");int oldBg=a.bg,oldCard=a.card,oldFg=a.fg;
            try{
                for(boolean light:new boolean[]{false,true})for(boolean carnet:new boolean[]{false,true})for(int screenWidth:new int[]{320,360,411}){
                    a.bg=light?Color.WHITE:Color.BLACK;a.card=light?Color.WHITE:Color.DKGRAY;a.fg=light?Color.BLACK:Color.WHITE;
                    LocalStore.put(local.obj("perfil"),"correo","daj2033135@est.univalle.edu");
                    LinearLayout fields=a.col();a.profileFields(fields,carnet);
                    int width=a.dp(screenWidth-44);
                    fields.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));
                    fields.layout(0,0,width,fields.getMeasuredHeight());
                    TextView email=findText(fields,"daj2033135@est.univalle.edu");
                    android.text.Layout layout=email.getLayout();
                    check(email.getMaxLines()==1&&layout!=null&&layout.getLineCount()==1,"Email stays on one line in "+(carnet?"Carnet Usuario":"profile")+" at "+screenWidth+"dp");
                    check(layout.getEllipsisCount(0)==0&&layout.getLineWidth(0)<=email.getWidth()+1,"Full email fits inside its value column");
                    LinearLayout row=(LinearLayout)email.getParent();
                    check(email.getRight()<=row.getWidth()&&email.getCurrentTextColor()==a.fg,"Email stays aligned and matches other field colors");
                }
            }finally{
                LocalStore.put(local.obj("perfil"),"correo",original);a.bg=oldBg;a.card=oldCard;a.fg=oldFg;
            }
        });
    }
    void checkLogin(MainActivity a)throws Exception{
        runOnMainSync(()->{
            a.biometrics.cancel();
            check(!a.authenticated&&a.route.equals("acceso"),"Cold launch requires authentication");
            a.route="carnet";a.render();check(a.route.equals("acceso"),"Protected route cannot bypass login");
            check(findText(a.frame,"INICIAR SESIÓN")!=null,"Reference login button");
            check(findText(a.frame,"Política de privacidad")!=null,"Privacy link");
            check(findDescription(a.frame,"Entrar con biometría")==null,"Login has no fingerprint icon");
            android.graphics.Paint fontPaint=new android.graphics.Paint();fontPaint.setTypeface(a.loginScreen.handwriting);
            check(fontPaint.hasGlyph("Ñ")&&fontPaint.hasGlyph("ñ")&&fontPaint.hasGlyph("Ó"),"Login font includes Spanish glyphs");
            check(a.loginScreen.password.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod,"PIN is concealed");
            a.loginScreen.submit();check(a.loginScreen.alert.isShowing(),"Empty PIN opens alert");
            check(findText(a.loginScreen.alert.getWindow().getDecorView(),"Debe ingresar un pin de usuario")!=null,"Reference empty PIN message");
            a.loginScreen.alert.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            // Native positive buttons post dismissal; close before the next same-thread assertion.
            a.loginScreen.alert.dismiss();
            a.loginScreen.password.setText("000000");a.loginScreen.submit();
            check(!a.authenticated&&!a.busy,"Incorrect password cannot enter");
            check(findText(a.loginScreen.alert.getWindow().getDecorView(),"Contraseña incorrecta")!=null,"Incorrect PIN message");
            a.loginScreen.alert.dismiss();
            a.loginScreen.switchAccount(true);check(a.loginScreen.username.getVisibility()==android.view.View.VISIBLE,"Other account reveals username");
            a.loginScreen.username.setText("unknown");a.loginScreen.password.setText("154745");a.loginScreen.submit();
            check(!a.authenticated&&!a.busy,"Unknown account cannot enter with local PIN");a.loginScreen.alert.dismiss();
            a.loginScreen.switchAccount(false);
        });
        IntentFilter filter=new IntentFilter(Intent.ACTION_VIEW);filter.addDataScheme("https");
        ActivityMonitor browser=addMonitor(filter,new ActivityResult(Activity.RESULT_OK,null),true);
        runOnMainSync(()->findText(a.frame,"Política de privacidad").performClick());
        check(browser.getHits()==1,"Privacy link launches an external browser intent");removeMonitor(browser);
        check(MainActivity.PRIVACY_URL.equals("https://app.univalle.edu/politicaseguridad/politicaseguridad.html"),"Exact reference policy URL");
        captureLogin(a,"login");
        runOnMainSync(()->a.loginScreen.submit());captureLogin(a,"login-empty-pin");
        runOnMainSync(()->a.loginScreen.alert.dismiss());
        passwordLogin(a);
        runOnMainSync(()->{a.logout();check(!a.authenticated&&a.route.equals("acceso")&&a.history.isEmpty(),"Logout locks screen and clears history");a.go("perfil");check(a.route.equals("acceso"),"Navigation stays locked after logout");});
        passwordLogin(a);
    }
    void passwordLogin(MainActivity a){
        runOnMainSync(()->{a.loginScreen.password.setText("154745");findText(a.frame,"INICIAR SESIÓN").performClick();check(a.busy&&!a.authenticated,"Successful PIN shows loading before home");});
        waitForLogin(a);
    }
    MainActivity checkLockedRecreation(MainActivity a){
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        runOnMainSync(()->{a.logout();a.loginScreen.switchAccount(true);a.loginScreen.password.setText("154745");a.recreate();});
        MainActivity recreated=(MainActivity)waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);
        check(recreated!=null,"Locked activity recreates");waitForIdleSync();
        runOnMainSync(()->{
            check(!recreated.authenticated&&recreated.route.equals("acceso"),"Recreation cannot unlock login");
            check(recreated.loginScreen.anotherAccount,"Recreation preserves other-account selection");
            check(recreated.loginScreen.password.length()==0,"Recreation never saves the password");
            recreated.loginScreen.switchAccount(false);
        });passwordLogin(recreated);return recreated;
    }
    void waitForLogin(MainActivity a){
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1);
        runOnMainSync(()->a.loginHandler.postDelayed(entered::countDown,600));
        try{check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS),"Login transition completes");}catch(InterruptedException e){throw new AssertionError(e);}
        waitForIdleSync();runOnMainSync(()->check(a.authenticated&&a.route.equals("home")&&a.history.isEmpty(),"Valid authentication opens home without login in back stack"));
    }
    void captureLogin(MainActivity a,String name)throws Exception{
        if(!screenshots)return;
        java.util.concurrent.CountDownLatch drawn=new java.util.concurrent.CountDownLatch(1);
        runOnMainSync(()->{
            android.view.View target=a.loginScreen.alert!=null&&a.loginScreen.alert.isShowing()?a.loginScreen.alert.getWindow().getDecorView():a.frame;
            target.postOnAnimation(()->target.postOnAnimation(drawn::countDown));
        });
        check(drawn.await(5,java.util.concurrent.TimeUnit.SECONDS),"Login screenshot frame "+name);
        getUiAutomation().waitForIdle(200,5000);
        File directory=new File(getTargetContext().getExternalFilesDir(null),"visual-checks");directory.mkdirs();
        Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Login screenshot "+name);
        try(OutputStream out=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    TextView findText(android.view.View view,String label){if(view instanceof TextView t&&label.contentEquals(t.getText()))return t;if(view instanceof android.view.ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView found=findText(g.getChildAt(i),label);if(found!=null)return found;}return null;}
    android.view.View findDescription(android.view.View view,String description){if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof android.view.ViewGroup g)for(int i=0;i<g.getChildCount();i++){android.view.View found=findDescription(g.getChildAt(i),description);if(found!=null)return found;}return null;}
    int findImageResource(android.view.View view){int count=view instanceof ImageView?1:0;if(view instanceof android.view.ViewGroup g)for(int i=0;i<g.getChildCount();i++)count+=findImageResource(g.getChildAt(i));return count;}
    MainActivity checkCarnetRecreation(MainActivity activity){
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        runOnMainSync(()->{activity.route="carnet";activity.carnetUser=true;activity.render();activity.recreate();});
        MainActivity recreated=(MainActivity)waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);check(recreated!=null,"Activity recreated");waitForIdleSync();
        runOnMainSync(()->{check(recreated.route.equals("carnet")&&recreated.carnetUser,"Activity recreation restores carnet tab");check(findText(recreated.root,"Usuario").isSelected()&&findText(recreated.root,"Sede:")!=null,"Recreated carnet renders Usuario");});return recreated;
    }
    String decodeQr(Bitmap qr){
        int width=qr.getWidth(),height=qr.getHeight();int[] pixels=new int[width*height];qr.getPixels(pixels,0,width,0,0,width,height);
        try{return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width,height,pixels)))).getText();}catch(NotFoundException e){throw new AssertionError("Carnet QR must be readable",e);}
    }
    Bitmap displayedQr(MainActivity a){ImageView image=(ImageView)findDescription(a.root,"Código QR local, sin validación institucional");check(image!=null&&image.getVisibility()==android.view.View.VISIBLE,"QR is embedded in the carnet");return ((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap();}
    void refreshCarnet(MainActivity a,Runnable configure,Runnable verify) {
        java.util.concurrent.CountDownLatch finished=new java.util.concurrent.CountDownLatch(1);
        android.view.View[] views=new android.view.View[2];
        runOnMainSync(()->{
            configure.run();views[0]=findDescription(a.root,"Actualizar QR local");views[1]=findDescription(views[0],"refresh");
            check(views[0].isEnabled(),"Refresh button is initially enabled");views[0].performClick();
            android.animation.ObjectAnimator turn=a.carnetRefreshAnimator;
            if(turn!=null){
                check(turn.isStarted()&&!views[0].isEnabled(),"Refresh starts animation and blocks repeated taps");
                check(turn.getTarget()==views[1]&&turn.getPropertyName().equals("rotation"),"Only the refresh arrow rotates");
                views[0].performClick();check(a.carnetRefreshAnimator==turn,"Repeated click does not restart the refresh");
                turn.addListener(new android.animation.AnimatorListenerAdapter(){
                    @Override public void onAnimationEnd(android.animation.Animator animation){finished.countDown();}
                });
            }else finished.countDown(); // Android may have animations disabled.
            verify.run();
        });
        try{check(finished.await(5,java.util.concurrent.TimeUnit.SECONDS),"Refresh animation finishes");}catch(InterruptedException e){throw new AssertionError(e);}
        runOnMainSync(()->{
            check(a.carnetRefreshAnimator==null&&views[0].isEnabled(),"Completed refresh releases animator and enables button");
            check(views[1].getRotation()==0f,"Refresh arrow returns to its original position");
        });
    }
    void checkCarnet(MainActivity a,LocalStore local,String importedImage){
        JSONObject p=local.obj("perfil");String oldText=p.optString("qrTexto"),oldImage=p.optString("qrImagen");
        runOnMainSync(()->{
            LocalStore.put(p,"qrImagen","");LocalStore.put(p,"qrTexto","SIU-LOCAL:prueba-123");a.route="home";a.history.clear();a.carnetUser=true;a.render();findDescription(a.root,"Carnet virtual").performClick();
            check(a.route.equals("carnet")&&!a.carnetUser,"Opening carnet selects QR tab");check(findText(a.root,"Carnet").isSelected(),"Carnet tab is selected");check(decodeQr(displayedQr(a)).equals("SIU-LOCAL:prueba-123"),"Displayed QR encodes configured local text");check(findText(a.root,"Sede:")==null,"Carnet tab does not show user fields");check(a.carnetName("JOSÉ ALEJANDRO DIAZ ALI").equals("JOSÉ ALEJANDRO\nDIAZ ALI"),"Two-line carnet name keeps physical-card owner readable");
        });waitForIdleSync();
        refreshCarnet(a,()->LocalStore.put(p,"qrTexto","SIU-LOCAL:actualizado-á"),()->check(decodeQr(displayedQr(a)).equals("SIU-LOCAL:actualizado-á"),"Refresh regenerates QR from latest local content"));
        runOnMainSync(()->{
            int history=a.history.size();android.view.View refresh=findDescription(a.root,"Actualizar QR local"),arrow=findDescription(refresh,"refresh");refresh.performClick();
            findText(a.root,"Usuario").performClick();check(a.carnetRefreshAnimator==null&&refresh.isEnabled()&&arrow.getRotation()==0f,"Switching tabs cancels and resets refresh animation");check(a.carnetUser&&findText(a.root,"Usuario").isSelected(),"Usuario tab is functional and selected");check(findText(a.root,"Sede:")!=null&&findText(a.root,p.optString("correo"))!=null,"Usuario shows profile fields");check(findDescription(a.root,"Código QR local, sin validación institucional")==null,"Usuario hides QR");check(findDescription(a.root,"Bandera de Bolivia")!=null,"Usuario keeps portrait flag");check(a.history.size()==history,"Tab changes do not add back-stack entries");
            Bundle state=new Bundle();a.onSaveInstanceState(state);check(state.getBoolean("carnetUser")&&state.getString("route").equals("carnet"),"Selected carnet tab is saved for recreation");a.render();check(findText(a.root,"Usuario").isSelected(),"Rerender preserves selected tab");
            LocalStore.put(local.obj("ajustes"),"tema","Claro");a.render();check(a.bg!=Color.BLACK&&findText(a.root,"Usuario").isSelected(),"Carnet supports light theme without losing selection");LocalStore.put(local.obj("ajustes"),"tema","Oscuro");findText(a.root,"Carnet").performClick();check(!a.carnetUser&&decodeQr(displayedQr(a)).equals("SIU-LOCAL:actualizado-á"),"Switch back to embedded QR");
        });waitForIdleSync();
        String longPayload="siu-local-".repeat(80);
        refreshCarnet(a,()->LocalStore.put(p,"qrTexto",longPayload),()->check(decodeQr(displayedQr(a)).equals(longPayload),"QR grows beyond reference grid for larger valid payloads"));
        refreshCarnet(a,()->LocalStore.put(p,"qrTexto",""),()->check(findText(a.root,"Introduce el contenido del QR en Ajustes → Editar contenido → Perfil.")!=null,"Empty QR has actionable inline message"));
        refreshCarnet(a,()->LocalStore.put(p,"qrTexto","x".repeat(5000)),()->check(findText(a.root,"El contenido es demasiado largo para generar un QR.")!=null,"Oversized QR has inline error"));
        refreshCarnet(a,()->LocalStore.put(p,"qrImagen",importedImage),()->check(displayedQr(a).getPixel(0,0)==Color.MAGENTA,"Imported QR image takes priority over text"));
        runOnMainSync(()->{
            LocalStore.put(p,"qrTexto",oldText);LocalStore.put(p,"qrImagen",oldImage);a.back();check(a.route.equals("home"),"Back from carnet returns to entry screen, not previous tab");
            a.go("drawer");((android.view.View)findText(a.frame,"Carnet virtual").getParent()).performClick();check(a.route.equals("carnet")&&!a.carnetUser,"Drawer opens QR tab");
            android.view.View refresh=findDescription(a.root,"Actualizar QR local"),arrow=findDescription(refresh,"refresh");refresh.performClick();a.back();
            check(a.route.equals("home")&&a.carnetRefreshAnimator==null&&refresh.isEnabled()&&arrow.getRotation()==0f,"Leaving carnet cancels refresh without blocking navigation");
        });waitForIdleSync();
    }
    void captureScreens(MainActivity a,LocalStore local)throws Exception{
        // Screenshots use fresh reference data in memory, never overwrite the real store.
        runOnMainSync(()->{try{local.data=new JSONObject(new String(a.readAsset("defaults.json"),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}a.selectedGestion="";});
        File directory=new File(getTargetContext().getExternalFilesDir(null),"visual-checks");directory.mkdirs();
        JSONObject bounds=new JSONObject();
        for(String screen:new String[]{"home","grid","drawer","perfil","carnet","carnet-usuario","ajustes","Social","Materias","Académica","Recursos","Tip's","documentos:0","horarios:0","notas","grades","pagos","contactos:0","tipos","detalle:0","ayuda"}){
            runOnMainSync(()->{LocalStore.put(local.obj("ajustes"),"cuadricula",screen.equals("grid"));a.route=screen.equals("grid")?"home":screen.equals("grades")?"notas":screen.equals("carnet-usuario")?"carnet":screen;a.carnetUser=screen.equals("carnet-usuario");if(screen.equals("grades"))a.selectedGestion=local.arr("gestiones").optString(0);a.render();});waitForIdleSync();
            // A fresh frame after layout is necessary: idle alone can precede traversal.
            java.util.concurrent.CountDownLatch drawn=new java.util.concurrent.CountDownLatch(1);
            runOnMainSync(()->a.frame.postOnAnimation(()->a.frame.postOnAnimation(drawn::countDown)));
            check(drawn.await(5,java.util.concurrent.TimeUnit.SECONDS),"Screenshot frame "+screen);
            getUiAutomation().waitForIdle(200,5000);
            runOnMainSync(()->{int[] origin=new int[2];a.frame.getLocationOnScreen(origin);try{bounds.put(screen,new JSONArray(new int[]{origin[0],origin[1],origin[0]+a.frame.getWidth(),origin[1]+a.frame.getHeight()}));}catch(JSONException e){throw new IllegalStateException(e);}});
            Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Screenshot "+screen);
            try(OutputStream out=new FileOutputStream(new File(directory,screen.replace(':','-')+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        }
        try(java.io.Writer out=new OutputStreamWriter(new FileOutputStream(new File(directory,"bounds.json")),java.nio.charset.StandardCharsets.UTF_8)){out.write(bounds.toString(2));}
    }
}
