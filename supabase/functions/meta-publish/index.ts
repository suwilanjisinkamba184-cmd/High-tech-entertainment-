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

Deno.serve(async(req:Request)=>{
 const origin=req.headers.get("origin")??"";
 if(req.method==="OPTIONS"){if(!originAllowed(req))return new Response("Origin not allowed",{status:403,headers:corsHeaders});return new Response("ok",{headers:{...corsHeaders,"Access-Control-Allow-Origin":origin}})}
 if(!originAllowed(req))return reply({ok:false,error:"Origin not allowed."},403,origin);
 if(req.method!=="POST")return reply({ok:false,error:"POST required."},405,origin);
 const auth=await requireFounder(req);if(!auth.ok)return auth.response;
 const pageToken=Deno.env.get("META_PAGE_ACCESS_TOKEN")?.trim(),pageId=Deno.env.get("META_PAGE_ID")?.trim();
 if(!pageToken||!pageId)return reply({ok:false,status:"missing_page_credentials",missing:[!pageToken?"META_PAGE_ACCESS_TOKEN":null,!pageId?"META_PAGE_ID":null].filter(Boolean),message:"Add the Page access token and Page ID as Supabase Edge Function secrets. Secret values are never exposed."},424,origin);
 let input:{action?:string;message?:string};try{input=await req.json()}catch{return reply({ok:false,error:"Valid JSON body required."},400,origin)}
 if(input.action!=="publish_facebook")return reply({ok:false,error:"Unsupported action."},400,origin);
 const message=(input.message??"").trim();if(message.length<10||message.length>5000)return reply({ok:false,error:"Message must be between 10 and 5000 characters."},400,origin);
 try{
  const pageUrl=new URL("https://graph.facebook.com/v27.0/"+encodeURIComponent(pageId));pageUrl.searchParams.set("fields","id,name,tasks,instagram_business_account");pageUrl.searchParams.set("access_token",pageToken);
  const pageRes=await fetch(pageUrl),page=await pageRes.json();
  if(!pageRes.ok||page.error)return reply({ok:false,status:"page_access_denied",message:page.error?.message??"Meta rejected Page access.",meta_error_code:page.error?.code??null},502,origin);
  if(Array.isArray(page.tasks)&&!page.tasks.some((task:string)=>["CREATE_CONTENT","MANAGE"].includes(task)))return reply({ok:false,status:"missing_publish_task",page:{id:page.id,name:page.name},message:"The connected Facebook user does not have Page content publishing access."},403,origin);
  const publishUrl="https://graph.facebook.com/v27.0/"+encodeURIComponent(pageId)+"/feed";
  const form=new URLSearchParams({message,access_token:pageToken});
  const publishRes=await fetch(publishUrl,{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},body:form}),published=await publishRes.json();
  if(!publishRes.ok||published.error)return reply({ok:false,status:"publish_rejected",page:{id:page.id,name:page.name},message:published.error?.message??"Meta rejected the post.",meta_error_code:published.error?.code??null},502,origin);
  return reply({ok:true,status:"published",platform:"facebook",page:{id:page.id,name:page.name},post_id:published.id,post_url:published.id?"https://www.facebook.com/"+published.id:null,instagram_linked:Boolean(page.instagram_business_account?.id),message:"Facebook Page post published successfully."},200,origin);
 }catch{return reply({ok:false,status:"network_error",message:"Could not complete the Meta publishing request. Check Edge Function logs."},502,origin)}
});
