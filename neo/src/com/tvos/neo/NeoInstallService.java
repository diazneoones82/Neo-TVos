package com.tvos.neo;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.os.*;
import com.neotv.protocol.*;
import org.json.JSONObject;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;

/** User-opened, certificate-pinned LAN receiver in its own process. No silent installs. */
public final class NeoInstallService extends Service {
    private static volatile NeoInstallService live;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService disk=Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor requests=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4));
    private final Set<Socket> sockets=Collections.synchronizedSet(new HashSet<>());
    private final List<Messenger> listeners=new ArrayList<>();
    private volatile SSLServerSocket server;
    private volatile NeoPairing pairing;
    private volatile String phase="closed",message="Pairing is closed",ticket="",label="",pkg="",kind="apk";
    private volatile long received,total;
    private volatile long generation;
    private File folder;
    private NeoInstallWire manifest;
    private int installSession=-1;
    private final Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper(),request->{
        String operation=request.getData().getString("op","");
        if(request.replyTo!=null&&!listeners.contains(request.replyTo))listeners.add(request.replyTo);
        if(operation.equals("start")){if(pairing==null&&!phase.equals("starting")){phase="starting";message="Opening secure local pairing";publish();disk.execute(this::open);}else publish();}
        else if(operation.equals("status"))publish();
        else if(operation.equals("approve"))disk.execute(this::approve);
        else if(operation.equals("cancel")){generation++;disk.execute(()->cancel("Cancelled on TV"));}
        else if(operation.equals("close")){disk.execute(this::closePairing);listeners.remove(request.replyTo);}
        return true;
    }));
    @Override public void onCreate(){super.onCreate();live=this;}
    @Override public IBinder onBind(Intent intent){return endpoint.getBinder();}
    private synchronized JSONObject status(){
        JSONObject result=new JSONObject();try{result.put("state",phase).put("message",message).put("ticket",ticket).put("label",label).put("package",pkg).put("kind",kind).put("received",received).put("total",total)
            .put("model",Build.MANUFACTURER+" "+Build.MODEL).put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT).put("abis",new org.json.JSONArray(Arrays.asList(Build.SUPPORTED_ABIS)));
            if(pairing!=null)result.put("qr",pairing.text()).put("expires",pairing.expires);
        }catch(Exception ignored){}return result;
    }
    private void publish(){Bundle data=new Bundle();data.putString("status",status().toString());data.putInt("worker_pid",android.os.Process.myPid());data.putInt("worker_uid",android.os.Process.myUid());main.post(()->{for(Iterator<Messenger> it=listeners.iterator();it.hasNext();)try{Message result=Message.obtain(null,1);result.setData(data);it.next().send(result);}catch(Exception disconnected){it.remove();}});}
    private void state(String state,String detail){phase=state;message=detail;publish();}
    private void open(){
        try{
            NeoTlsIdentity identity=NeoTlsIdentity.load(new File(getFilesDir(),"local-installer-tls"));
            String alias="neo-local-receiver";PrivateKey key=identity.key;X509Certificate certificate=identity.certificate;
            X509ExtendedKeyManager manager=new X509ExtendedKeyManager(){
                public String[] getClientAliases(String type,java.security.Principal[] issuers){return null;}public String chooseClientAlias(String[] types,java.security.Principal[] issuers,Socket socket){return null;}
                public String[] getServerAliases(String type,java.security.Principal[] issuers){return type.equals("RSA")?new String[]{alias}:null;}
                public String chooseServerAlias(String type,java.security.Principal[] issuers,Socket socket){return type.equals("RSA")?alias:null;}
                public X509Certificate[] getCertificateChain(String selected){return new X509Certificate[]{certificate};}public PrivateKey getPrivateKey(String selected){return key;}
            };
            SSLContext tls=SSLContext.getInstance("TLS");tls.init(new KeyManager[]{manager},null,new SecureRandom());
            String localHost=localAddress();SSLServerSocket listener=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(0,4,InetAddress.getByName(localHost));listener.setEnabledProtocols(new String[]{"TLSv1.2"});
            byte[] secret=new byte[16];new SecureRandom().nextBytes(secret);
            pairing=new NeoPairing(localHost,listener.getLocalPort(),secret,MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()),System.currentTimeMillis()/1000+600);
            server=listener;state("ready","Scan this QR in the phone companion. Pairing expires in 10 minutes.");
            main.postDelayed(()->disk.execute(()->{if(pairing!=null&&System.currentTimeMillis()/1000>=pairing.expires){closePairing();state("expired","Pairing expired. Close and reopen this screen for a new QR.");}}),600000);
            Thread accept=new Thread(()->{while(!listener.isClosed())try{
                Socket client=listener.accept();client.setSoTimeout(120000);sockets.add(client);
                try{requests.execute(()->serve(client));}catch(RejectedExecutionException busy){sockets.remove(client);client.close();}
            }catch(IOException closed){break;}},"neo-install-listener");accept.start();
        }catch(Exception error){closePairing();state("error","Could not open pairing: "+error.getClass().getSimpleName()+". Check the TV's network.");}
    }
    private String localAddress()throws Exception{
        ConnectivityManager network=getSystemService(ConnectivityManager.class);LinkProperties properties=network.getLinkProperties(network.getActiveNetwork());
        if(properties!=null)for(LinkAddress address:properties.getLinkAddresses())if(address.getAddress() instanceof Inet4Address && NeoPairing.localAddress(address.getAddress().getHostAddress()))return address.getAddress().getHostAddress();
        for(NetworkInterface adapter:Collections.list(NetworkInterface.getNetworkInterfaces()))if(adapter.isUp()&&!adapter.isLoopback()&&!adapter.getName().startsWith("tun"))for(InetAddress address:Collections.list(adapter.getInetAddresses()))if(address instanceof Inet4Address&&NeoPairing.localAddress(address.getHostAddress()))return address.getHostAddress();
        throw new IOException("Connect the TV to local Wi-Fi or Ethernet");
    }
    private static String line(DataInputStream input)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();for(int i=0;i<4096;i++){int b=input.read();if(b<0)throw new EOFException();if(b==10)return bytes.toString("US-ASCII").replace("\r","");bytes.write(b);}throw new IOException("HTTP header too long");}
    private void respond(OutputStream out,int code,JSONObject json)throws IOException{byte[] body=json.toString().getBytes("UTF-8");ByteArrayOutputStream response=new ByteArrayOutputStream();response.write(("HTTP/1.1 "+code+" Neo\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));response.write(body);out.write(response.toByteArray());out.flush();}
    private void serve(Socket socket){
        String uploadTicket="";
        Socket client=socket;
        try{
            DataInputStream in=new DataInputStream(new BufferedInputStream(client.getInputStream()));
            String[] first=line(in).split(" ");if(first.length!=3)throw new IOException("Invalid request");
            Map<String,String> headers=new HashMap<>();int size=0;for(String next;(next=line(in)).length()>0;){size+=next.length();if(size>16384)throw new IOException("Headers too large");int colon=next.indexOf(':');if(colon<1)throw new IOException("Invalid header");headers.put(next.substring(0,colon).toLowerCase(Locale.ROOT),next.substring(colon+1).trim());}
            NeoPairing current=pairing;if(current==null||System.currentTimeMillis()/1000>=current.expires||!current.authorizes(headers.getOrDefault("authorization",""))){respond(client.getOutputStream(),401,new JSONObject().put("error","Pairing expired or unauthorized"));return;}
            if(first[0].equals("GET")&&(first[1].equals("/info")||first[1].equals("/status"))){JSONObject result=status();result.remove("qr");respond(client.getOutputStream(),200,result);return;}
            if(!first[0].equals("POST")){respond(client.getOutputStream(),404,new JSONObject());return;}
            long length=Long.parseLong(headers.getOrDefault("content-length","-1"));if(length<0||length>NeoInstallWire.MAX_TOTAL+65536)throw new IOException("Invalid request length");
            if(first[1].equals("/install")){
                long version;
                synchronized(this){if(Arrays.asList("receiving","review","installing","confirm").contains(phase)){respond(client.getOutputStream(),409,new JSONObject().put("error","Finish or cancel the current TV request first"));return;}phase="receiving";ticket=UUID.randomUUID().toString();uploadTicket=ticket;version=++generation;kind="apk";received=0;total=length;}
                receive(in,length,version);respond(client.getOutputStream(),202,status());return;
            }
            if(first[1].equals("/store")){
                if(length>1024)throw new IOException("Store request too large");byte[] body=new byte[(int)length];in.readFully(body);JSONObject request=new JSONObject(new String(body,"UTF-8"));String wanted=request.getString("package");
                if(!wanted.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")||wanted.length()>180)throw new IOException("Invalid Play Store package");
                synchronized(this){if(Arrays.asList("receiving","review","installing","confirm").contains(phase))throw new IOException("Finish current TV request first");ticket=UUID.randomUUID().toString();kind="store";pkg=wanted;label=wanted;}
                state("review","Phone requests this Play Store listing. Approve on TV to open Google Play; install using the store.");respond(client.getOutputStream(),202,status());return;
            }
            if(first[1].equals("/cancel")){cancel("Cancelled from phone");respond(client.getOutputStream(),200,status());return;}
            respond(client.getOutputStream(),404,new JSONObject());
        }catch(Exception failure){android.util.Log.w("NeoInstall","Connection ended: "+failure.getClass().getSimpleName()+": "+failure.getMessage());if(!uploadTicket.isEmpty()&&uploadTicket.equals(ticket)&&phase.equals("receiving")){cleanup();state("error","Transfer rejected: "+failure.getMessage()+". Nothing was installed.");}try{respond(client.getOutputStream(),422,new JSONObject().put("error",failure.getMessage()==null?"Invalid request":failure.getMessage()));}catch(Exception closed){}}
        finally{sockets.remove(socket);try{socket.close();}catch(IOException ignored){}}
    }
    private void receive(DataInputStream input,long length,long version)throws Exception{
        NeoInstallWire transfer=NeoInstallWire.read(input);if(transfer.length()!=length)throw new IOException("Upload length mismatch");
        if(!transfer.parts.get(0).name.equals("base.apk"))throw new IOException("Base APK must be first");
        File staging;
        synchronized(this){
            if(version!=generation)throw new IOException("Transfer cancelled");cleanup();
            staging=new File(getCacheDir(),"remote-"+ticket);if(!staging.mkdir())throw new IOException("Cannot stage APKs");
            folder=staging;manifest=transfer;label=transfer.label;pkg=transfer.pkg;
        }
        publish();byte[] buffer=new byte[65536];long countSoFar=0,published=0;
        for(NeoInstallWire.Part part:transfer.parts){
            MessageDigest digest=MessageDigest.getInstance("SHA-256");File file=new File(staging,part.name);
            try(FileOutputStream output=new FileOutputStream(file)){long remaining=part.size;while(remaining>0){
                if(version!=generation)throw new IOException("Transfer cancelled");int count=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(count<0)throw new EOFException();
                if(version!=generation)throw new IOException("Transfer cancelled");output.write(buffer,0,count);digest.update(buffer,0,count);remaining-=count;countSoFar+=count;
                if(version==generation){received=countSoFar;if(countSoFar-published>1024*1024){published=countSoFar;publish();}}
            }output.getFD().sync();}
            if(!MessageDigest.isEqual(part.digest,digest.digest()))throw new IOException("APK hash mismatch");
        }
        if(version!=generation)throw new IOException("Transfer cancelled");
        File base=new File(staging,"base.apk");PackageInfo archive=getPackageManager().getPackageArchiveInfo(base.getPath(),0);
        if(archive==null||!transfer.pkg.equals(archive.packageName))throw new IOException("APK identity mismatch");
        if(transfer.pkg.equals("android")||transfer.pkg.startsWith("com.android.")||transfer.pkg.equals(getPackageName()))throw new IOException("Core/launcher packages cannot be replaced through remote app transfer");
        try{ApplicationInfo installed=getPackageManager().getApplicationInfo(transfer.pkg,0);if((installed.flags&ApplicationInfo.FLAG_SYSTEM)!=0)throw new IOException("Use Google Play for preinstalled/system app updates");}catch(PackageManager.NameNotFoundException newApp){}
        if(archive.applicationInfo!=null&&archive.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new IOException("This app requires a newer Android version");
        String actualLabel=transfer.label;if(archive.applicationInfo!=null){archive.applicationInfo.sourceDir=base.getPath();archive.applicationInfo.publicSourceDir=base.getPath();actualLabel=archive.applicationInfo.loadLabel(getPackageManager()).toString();}
        synchronized(this){if(version!=generation)throw new IOException("Transfer cancelled");label=actualLabel;phase="review";message="Received "+transfer.parts.size()+" APK file(s). Review this app and approve installation on the TV.";}
        publish();
    }
    private void approve(){
        if(!phase.equals("review"))return;
        if(kind.equals("store")){state("store-open","Opening the official TV Play Store; finish installation there.");final String storePackage=pkg;main.post(()->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+storePackage)).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
            catch(RuntimeException missing){state("error","Google Play Store is not installed on this TV. Transfer a compatible APK instead.");}
        });return;}
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){state("review","Enable Install unknown apps for Neo TVLauncher, then approve again.");return;}
        final long version=generation;final NeoInstallWire transfer=manifest;final File staging=folder;int created=-1;
        try{
            if(transfer==null||staging==null)throw new IOException("No staged app");
            state("installing","Preparing Android installation; cancel remains available");
            PackageInstaller installer=getPackageManager().getPackageInstaller();PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(transfer.pkg);params.setSize(transfer.parts.stream().mapToLong(part->part.size).sum());
            if(version!=generation)throw new IOException("Cancelled");created=installer.createSession(params);installSession=created;
            try(PackageInstaller.Session session=installer.openSession(created)){
                byte[] buffer=new byte[65536];for(NeoInstallWire.Part part:transfer.parts)try(InputStream input=new FileInputStream(new File(staging,part.name));OutputStream output=session.openWrite(part.name,0,part.size)){
                    for(int n;(n=input.read(buffer))!=-1;){if(version!=generation)throw new IOException("Cancelled");output.write(buffer,0,n);}session.fsync(output);
                }
                if(version!=generation)throw new IOException("Cancelled");
                Intent result=new Intent(this,NeoInstallResult.class).setAction("com.tvos.neo.INSTALL_RESULT").putExtra("neo_ticket",ticket);
                int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
                state("installing","Waiting for Android package installer confirmation on the TV");session.commit(PendingIntent.getBroadcast(this,created,result,flags).getIntentSender());
            }
        }catch(Exception failure){
            if(created>=0)try{getPackageManager().getPackageInstaller().abandonSession(created);}catch(RuntimeException ignored){}
            if(version==generation){state("error","Android rejected the install session: "+failure.getMessage());cleanup();}
        }
    }
    static void result(Intent intent){NeoInstallService service=live;if(service!=null)service.main.post(()->service.onResult(intent));}
    private void onResult(Intent intent){
        if(!ticket.equals(intent.getStringExtra("neo_ticket")))return;int result=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(result==PackageInstaller.STATUS_PENDING_USER_ACTION){Intent approval=intent.getParcelableExtra(Intent.EXTRA_INTENT);if(approval==null){state("error","Android did not provide install confirmation");return;}state("confirm","Confirm the Android installation dialog on the TV");try{startActivity(approval.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException unavailable){state("error","Could not display Android install confirmation");}return;}
        if(result==PackageInstaller.STATUS_SUCCESS)state("installed","App installed successfully on this TV");
        else state("error","Android did not install this app: "+intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        cleanup();installSession=-1;
    }
    private synchronized void cancel(String reason){generation++;if(installSession>=0)try{getPackageManager().getPackageInstaller().abandonSession(installSession);}catch(RuntimeException ignored){}installSession=-1;cleanup();state("cancelled",reason);}
    private synchronized void cleanup(){if(folder!=null){File[] files=folder.listFiles();if(files!=null)for(File file:files)if(file.isFile())file.delete();folder.delete();folder=null;}manifest=null;}
    private void closePairing(){pairing=null;if(server!=null)try{server.close();}catch(IOException ignored){}server=null;synchronized(sockets){for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}sockets.clear();}cancel("Pairing is closed");phase="closed";publish();}
    @Override public void onDestroy(){live=null;closePairing();main.removeCallbacksAndMessages(null);requests.shutdownNow();disk.shutdownNow();super.onDestroy();}
}
