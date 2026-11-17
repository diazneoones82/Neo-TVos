"""LAN integration checks against a user-opened TV pairing screen; fixture only.

Requires Build-RemoteFixture.ps1 output and a fresh pairing URI in an ignored file.
Never approves PackageInstaller or installs/removes an app.
"""
from pathlib import Path
import argparse,base64,hashlib,http.client,json,socket,ssl,struct

root=Path(__file__).resolve().parents[1]
options=argparse.ArgumentParser()
options.add_argument('--pair-file',type=Path,default=root/'audit/live-pairing.txt')
args=options.parse_args()
encoded=args.pair_file.read_text().strip().split('/')[-1]
packet=base64.urlsafe_b64decode(encoded+'='*((-len(encoded))%4))
assert len(packet)==59
host='.'.join(map(str,packet[1:5]));port=int.from_bytes(packet[5:7],'big')
secret=base64.urlsafe_b64encode(packet[7:23]).decode().rstrip('=');pin=packet[23:55]

def request(method,path,body=b'',authorization=None,declared=None):
    context=ssl._create_unverified_context()
    connection=http.client.HTTPSConnection(host,port,context=context,timeout=15)
    try:
        connection.connect()
        assert hashlib.sha256(connection.sock.getpeercert(binary_form=True)).digest()==pin,'TLS identity changed'
        headers={'Content-Length':str(len(body) if declared is None else declared),'Connection':'close'}
        if authorization is not None:headers['Authorization']=authorization
        connection.request(method,path,body,headers)
        response=connection.getresponse();data=json.loads(response.read());return response.status,data
    finally:connection.close()

auth='Bearer '+secret
assert request('GET','/info')[0]==401
assert request('GET','/info',authorization='Bearer '+('0'*22))[0]==401
code,info=request('GET','/info',authorization=auth)
assert code==200 and info['model'] and 'qr' not in info

def utf(value):
    data=value.encode('ascii');return struct.pack('>H',len(data))+data
files=[(root/'audit/remote-fixture'/name).read_bytes() for name in ['base.apk','split.apk']]
def upload(bad_digest=False,bad_name=False):
    header=b'NEO1'+utf('Neo Remote Test')+utf('org.neotv.remote.fixture')+struct.pack('>H',2)
    for i,data in enumerate(files):header+=utf('../base.apk' if bad_name and i==0 else 'base.apk' if i==0 else 'split-1.apk')+struct.pack('>Q',len(data))+(bytes(32) if bad_digest else hashlib.sha256(data).digest())
    return header+b''.join(files)

assert request('POST','/install',upload(bad_digest=True),auth)[0]==422
assert request('GET','/status',authorization=auth)[1]['state']=='error'
assert request('POST','/install',upload(bad_name=True),auth)[0]==422
assert request('POST','/install',b'',auth,declared=1024**3+65537)[0]==422
assert request('POST','/store',json.dumps({'package':'x; bad'}).encode(),auth)[0]==422
code,review=request('POST','/install',upload(),auth)
assert code==202 and review['state']=='review'
assert request('POST','/install',upload(),auth)[0]==409
assert request('POST','/cancel',b'{}',auth)[1]['state']=='cancelled'
assert request('GET','/status',authorization=auth)[1]['state']=='cancelled'
print('PASS: pinned TLS, missing/wrong-token rejection, hash/path/size/package guards, busy refusal and cancellation without install approval')
