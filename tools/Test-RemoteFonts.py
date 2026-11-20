"""Exercise a user-opened font-only TV receiver; never approves an import."""
import argparse,base64,hashlib,http.client,json,ssl,struct
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--pair-file',type=Path,required=True);p.add_argument('--font',type=Path,required=True);args=p.parse_args()
s=args.pair_file.read_text().strip().split('/')[-1];packet=base64.urlsafe_b64decode(s+'='*((-len(s))%4));host='.'.join(map(str,packet[1:5]));port=int.from_bytes(packet[5:7],'big');pin=packet[23:55];token=base64.urlsafe_b64encode(packet[7:23]).decode().rstrip('=')
def request(method,path,body=b'',declared=None,auth=True):
 c=http.client.HTTPSConnection(host,port,context=ssl._create_unverified_context(),timeout=15)
 try:
  c.connect();assert hashlib.sha256(c.sock.getpeercert(binary_form=True)).digest()==pin
  headers={'Content-Length':str(len(body) if declared is None else declared)}
  if auth:headers['Authorization']='Bearer '+token
  c.request(method,path,body,headers);r=c.getresponse();return r.status,json.loads(r.read())
 finally:c.close()
def utf(t):
 b=t.encode('ascii');return struct.pack('>H',len(b))+b
def payload(data,name='Neo-Test.ttf',bad_hash=False):return b'NEOF'+utf(name)+struct.pack('>Q',len(data))+(bytes(32) if bad_hash else hashlib.sha256(data).digest())+data
font=args.font.read_bytes()
assert request('GET','/info',auth=False)[0]==401
code,info=request('GET','/info');assert code==200 and info['supports_fonts'] and not info['supports_apps']
if info['state']=='review' and info.get('label')=='Neo-Test.ttf' and info.get('kind')=='font':request('POST','/cancel',b'{}')
assert request('POST','/install')[0]==422
assert request('POST','/font',payload(b'invalid font data',name='../font.ttf'))[0]==422
assert request('POST','/font',payload(font,bad_hash=True))[0]==422
assert request('POST','/font',payload(b'not a font'*20))[0]==422
assert request('POST','/font',declared=10*1024*1024+513)[0]==422
code,review=request('POST','/font',payload(font));assert code==202 and review['state']=='review' and review['kind']=='font'
assert not review['font_id'],'Imported before TV approval'
assert request('POST','/font')[0]==409
assert request('POST','/cancel',b'{}')[1]['state']=='cancelled'
print('PASS: pinned font-only pairing, unauthenticated/APK/path/hash/format/size rejection, review-before-import, busy refusal and cancellation')
