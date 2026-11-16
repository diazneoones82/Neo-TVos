package com.neotvos.support;
import android.hardware.usb.*;import java.io.IOException;
/** Android phone is USB host; TV must expose a USB ADB device interface. */
final class UsbWire implements AdbClient.Wire{
 final UsbDeviceConnection connection;final UsbInterface intf;final UsbEndpoint in,out;private byte[] pending=new byte[0];private int offset;
 static UsbInterface adbInterface(UsbDevice d){for(int i=0;i<d.getInterfaceCount();i++){UsbInterface f=d.getInterface(i);if(f.getInterfaceClass()==255&&f.getInterfaceSubclass()==66&&f.getInterfaceProtocol()==1)return f;}return null;}
 UsbWire(UsbManager manager,UsbDevice device)throws Exception{intf=adbInterface(device);if(intf==null)throw new IOException("No USB ADB interface. Use a data/OTG cable and the TV's device/debug port.");UsbEndpoint ei=null,eo=null;for(int i=0;i<intf.getEndpointCount();i++){UsbEndpoint e=intf.getEndpoint(i);if(e.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){if(e.getDirection()==UsbConstants.USB_DIR_IN)ei=e;else eo=e;}}if(ei==null||eo==null)throw new IOException("USB ADB endpoints missing");in=ei;out=eo;connection=manager.openDevice(device);if(connection==null)throw new IOException("USB permission denied or device detached");if(!connection.claimInterface(intf,true)){connection.close();throw new IOException("Could not claim USB ADB interface");}}
 public void readFully(byte[] b)throws Exception{int at=0;while(at<b.length){if(offset<pending.length){int n=Math.min(b.length-at,pending.length-offset);System.arraycopy(pending,offset,b,at,n);offset+=n;at+=n;continue;}byte[] data=new byte[16384];int n=connection.bulkTransfer(in,data,data.length,90000);if(n<=0)throw new IOException("USB timed out. Approve debugging on the TV or reconnect.");pending=java.util.Arrays.copyOf(data,n);offset=0;}}
 public void write(byte[] b)throws Exception{for(int at=0;at<b.length;){int n=Math.min(16384,b.length-at);int done=connection.bulkTransfer(out,b,at,n,30000);if(done<=0)throw new IOException("USB disconnected during transfer");at+=done;}}
 public void close(){connection.releaseInterface(intf);connection.close();}
}
