import { createClient } from "npm:@supabase/supabase-js@2";
const OFFICIAL_ORIGIN="https://suwilanjisinkamba184-cmd.github.io";
const corsHeaders={"Access-Control-Allow-Origin":OFFICIAL_ORIGIN,"Access-Control-Allow-Headers":"authorization, x-client-info, apikey, content-type","Access-Control-Allow-Methods":"POST, OPTIONS","Vary":"Origin"};
function reply(body:unknown,status=200,origin=""){return new Response(JSON.stringify(body),{status,headers:{...corsHeaders,"Access-Control-Allow-Origin":origin===OFFICIAL_ORIGIN?origin:OFFICIAL_ORIGIN,"Content-Type":"application/json","Cache-Control":"no-store"}})}
function originAllowed(req:Request){return req.headers.get("origin")===OFFICIAL_ORIGIN}
async function requireFounder(req:Request):Promise<{ok:true}|{ok:false;response:Response}>{
 const authorization=req.headers.get("Authorization")??"";
 if(!/^Bearer\s+\S+$/i.test(authorization))return {ok:false,response:reply({ok:false,error:"Founder sign-in required."},401,req.headers.get("origin")??"")};
 const url=Deno.env.get("SUPABASE_URL")?.trim(),key=Deno.env.get("SUPABASE_PUBLISHABLE_KEY")?.trim()||Deno.env.get("SUPABASE_ANON_KEY")?.trim();
 if(!url||!key)return {ok:false,response:reply({ok:false,error:"Founder authorization service is not configured."},503,req.headers.get("origin")??"")};
 const token=authorization.replace(/^Bearer\s+/i,"");
 const client=createClient(url,key,{auth:{persistSession:false,autoRefreshToken:false},global:{headers:{Authorization:"Bearer "+token}}});
 const {data,error}=await client.auth.getUser(token);
 if(error||!data.user)return {ok:false,response:reply({ok:false,error:"Your founder session is invalid or expired. Sign in again."},401,req.headers.get("origin")??"")};
 const {data:isAdmin,error:roleError}=await client.rpc("hte_is_admin");
 if(roleError||isAdmin!==true)return {ok:false,response:reply({ok:false,error:"Founder authorization required."},403,req.headers.get("origin")??"")};
 return {ok:true};
}

async function appSecretProof(token:string,secret:string):Promise<string>{
 const key=await crypto.subtle.importKey("raw",new TextEncoder().encode(secret),{name:"HMAC",hash:"SHA-256"},false,["sign"]);
 const signature=await crypto.subtle.sign("HMAC",key,new TextEncoder().encode(token));
 return [...new Uint8Array(signature)].map(b=>b.toString(16).padStart(2,"0")).join("");
}
Deno.serve(async(req:Request)=>{
 const origin=req.headers.get("origin")??"";
 if(req.method==="OPTIONS"){if(!originAllowed(req))return new Response("Origin not allowed",{status:403,headers:corsHeaders});return new Response("ok",{headers:{...corsHeaders,"Access-Control-Allow-Origin":origin}})}
 if(!originAllowed(req))return reply({ok:false,error:"Origin not allowed."},403,origin);
 if(req.method!=="POST")return reply({ok:false,error:"Use POST to run the Meta connection check."},405,origin);
 const auth=await requireFounder(req);if(!auth.ok)return auth.response;
 const appId=Deno.env.get("META_APP_ID")?.trim(),appSecret=Deno.env.get("META_APP_SECRET")?.trim(),accessToken=Deno.env.get("META_ACCESS_TOKEN")?.trim();
 const missing=[!appId?"META_APP_ID":null,!appSecret?"META_APP_SECRET":null,!accessToken?"META_ACCESS_TOKEN":null].filter(Boolean);
 if(missing.length)return reply({ok:false,connected:false,status:"missing_secrets",missing,message:"Add the missing Meta credentials in Supabase Edge Function Secrets. Secret values are never returned."},424,origin);
 try{
  const appAccessToken=appId+"|"+appSecret,debugUrl=new URL("https://graph.facebook.com/v27.0/debug_token");
  debugUrl.searchParams.set("input_token",accessToken!);debugUrl.searchParams.set("access_token",appAccessToken);
  const debugResponse=await fetch(debugUrl),debugBody=await debugResponse.json();
  if(!debugResponse.ok||debugBody.error)return reply({ok:false,connected:false,status:"meta_rejected_credentials",message:debugBody.error?.message??"Meta could not validate the configured token.",meta_error_code:debugBody.error?.code??null},502,origin);
  const tokenData=debugBody.data??{};
  if(tokenData.is_valid!==true)return reply({ok:false,connected:false,status:"invalid_or_expired_token",token_valid:false,expires_at:tokenData.expires_at??null,message:"Meta reports that the configured token is invalid or expired."},200,origin);
  if(tokenData.app_id&&tokenData.app_id!==appId)return reply({ok:false,connected:false,status:"token_belongs_to_different_app",token_valid:true,app_id_matches:false,message:"Generate the token from the High-Tech Entertainment Meta app."},200,origin);
  const proof=await appSecretProof(accessToken!,appSecret!),meUrl=new URL("https://graph.facebook.com/v27.0/me");
  meUrl.searchParams.set("fields","id,name");meUrl.searchParams.set("access_token",accessToken!);meUrl.searchParams.set("appsecret_proof",proof);
  const meResponse=await fetch(meUrl),meBody=await meResponse.json();
  if(!meResponse.ok||meBody.error)return reply({ok:false,connected:false,status:"token_valid_but_profile_check_failed",token_valid:true,message:meBody.error?.message??"Meta validated the token but the profile check failed.",meta_error_code:meBody.error?.code??null},502,origin);
  return reply({ok:true,connected:true,status:"connected",app_id_matches:true,token_valid:true,identity:{id:meBody.id??null,name:meBody.name??null},token_type:tokenData.type??null,expires_at:tokenData.expires_at??null,data_access_expires_at:tokenData.data_access_expires_at??null,scopes:Array.isArray(tokenData.scopes)?tokenData.scopes:[],message:"Meta credentials validated. This checks authentication only; Page and Instagram asset-level publishing permissions require separate verification."},200,origin);
 }catch{return reply({ok:false,connected:false,status:"connection_error",message:"The Meta verification request failed. Check Edge Function logs and retry."},502,origin)}
});
