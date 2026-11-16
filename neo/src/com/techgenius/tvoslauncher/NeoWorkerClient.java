package com.tvos.neo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Short-lived Binder requests. Worker crashes/timeouts do not block the UI process. */
final class NeoWorkerClient {
    interface Reply {void accept(Bundle result);}
    static void request(Context context,Class<?> service,Bundle data,long timeoutMs,Reply reply){
        Context app=context.getApplicationContext();Handler main=new Handler(Looper.getMainLooper());
        main.post(()->{
            final boolean[] finished={false},bound={false};final ServiceConnection[] connection={null};
            final Runnable[] timeout={null};
            Reply finish=result->{if(finished[0])return;finished[0]=true;main.removeCallbacks(timeout[0]);if(bound[0])try{app.unbindService(connection[0]);}catch(Exception ignored){}if(android.util.Log.isLoggable("NeoWorkers",android.util.Log.DEBUG))android.util.Log.d("NeoWorkers",service.getSimpleName()+" pid="+result.getInt("worker_pid",-1)+" uid="+result.getInt("worker_uid",-1)+" error="+result.getString("error","none"));reply.accept(result);};
            timeout[0]=()->{Bundle error=new Bundle();error.putString("error","Worker timed out");finish.accept(error);};
            Messenger receiver=new Messenger(new Handler(Looper.getMainLooper(),message->{finish.accept(message.getData());return true;}));
            connection[0]=new ServiceConnection(){
                @Override public void onServiceConnected(ComponentName name,IBinder binder){try{Message request=Message.obtain(null,1);request.setData(data);request.replyTo=receiver;new Messenger(binder).send(request);}catch(Exception e){Bundle error=new Bundle();error.putString("error","Worker connection failed");finish.accept(error);}}
                @Override public void onServiceDisconnected(ComponentName name){Bundle error=new Bundle();error.putString("error","Worker disconnected");finish.accept(error);}
                @Override public void onNullBinding(ComponentName name){Bundle error=new Bundle();error.putString("error","Worker unavailable");finish.accept(error);}
            };
            main.postDelayed(timeout[0],timeoutMs);
            try{bound[0]=app.bindService(new Intent(app,service),connection[0],Context.BIND_AUTO_CREATE);if(!bound[0]){Bundle error=new Bundle();error.putString("error","Worker unavailable");finish.accept(error);}}
            catch(Exception e){Bundle error=new Bundle();error.putString("error","Worker unavailable");finish.accept(error);}
        });
    }
    static Bundle blocking(Context context,Class<?> service,Bundle data,long timeoutMs){
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("IPC wait must stay off the UI thread");
        CountDownLatch done=new CountDownLatch(1);Bundle[] result={new Bundle()};
        request(context,service,data,timeoutMs,response->{result[0]=response;done.countDown();});
        try{done.await(timeoutMs+1000,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        return result[0];
    }
    static void send(Messenger receiver,Bundle result){try{Message reply=Message.obtain(null,1);reply.setData(result);receiver.send(reply);}catch(Exception ignored){}}
}
