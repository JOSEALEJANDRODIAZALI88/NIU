package com.siu.local;

import android.app.AlertDialog;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;

/** Uses the system's enrolled biometrics; cancellation never authenticates. */
@SuppressWarnings("deprecation")
final class BiometricAccess {
    final MainActivity activity;
    CancellationSignal signal;
    AlertDialog legacyDialog;
    int generation;

    BiometricAccess(MainActivity activity){this.activity=activity;}
    boolean available(){
        if(Build.VERSION.SDK_INT>=29){
            BiometricManager manager=activity.getSystemService(BiometricManager.class);
            return manager!=null&&(Build.VERSION.SDK_INT>=30
                    ?manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    :manager.canAuthenticate())==BiometricManager.BIOMETRIC_SUCCESS;
        }
        FingerprintManager manager=activity.getSystemService(FingerprintManager.class);
        return manager!=null&&manager.isHardwareDetected()&&manager.hasEnrolledFingerprints();
    }
    void authenticate(){
        if(signal!=null||!available())return;
        final int request=++generation;signal=new CancellationSignal();
        if(Build.VERSION.SDK_INT>=28){
            BiometricPrompt.Builder builder=new BiometricPrompt.Builder(activity)
                    .setTitle("Iniciar sesión").setSubtitle("Usa tu biometría para entrar a SIU")
                    .setNegativeButton("Usar contraseña",activity.getMainExecutor(),(d,w)->cancel());
            if(Build.VERSION.SDK_INT>=30)builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK);
            builder.build().authenticate(signal,activity.getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){success(request);}
                @Override public void onAuthenticationError(int code,CharSequence message){error(request,code,message);}
                @Override public void onAuthenticationFailed(){/* Android keeps the prompt open for another attempt. */}
            });
        }else{
            legacyDialog=new AlertDialog.Builder(activity).setTitle("Iniciar sesión").setMessage("Toca el lector de huella")
                    .setNegativeButton("Usar contraseña",(d,w)->cancel()).setOnCancelListener(d->cancel()).show();
            activity.getSystemService(FingerprintManager.class).authenticate(null,signal,0,new FingerprintManager.AuthenticationCallback(){
                @Override public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result){success(request);}
                @Override public void onAuthenticationError(int code,CharSequence message){error(request,code,message);}
                @Override public void onAuthenticationFailed(){if(legacyDialog!=null)legacyDialog.setMessage("Huella no reconocida. Inténtalo de nuevo.");}
            },null);
        }
    }
    void success(int request){if(request!=generation||signal==null)return;cancel();activity.completeLogin();}
    void error(int request,int code,CharSequence message){
        if(request!=generation)return;cancel();
        // User/system cancellation quietly leaves the password screen available.
        if(code!=5&&code!=10&&code!=13)activity.toast(message.toString());
    }
    void cancel(){
        generation++;CancellationSignal pending=signal;signal=null;if(pending!=null)pending.cancel();
        if(legacyDialog!=null){legacyDialog.dismiss();legacyDialog=null;}
    }
}
