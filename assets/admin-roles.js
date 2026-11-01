(()=>{'use strict';
const $=s=>document.querySelector(s),cfg=window.HTE_SUPABASE_CONFIG||{};
const form=$('#role-form'),list=$('#role-list'),status=$('#role-status');
if(!form||!list)return;
const db=window.supabase?.createClient(cfg.url,cfg.publishableKey,{auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true,flowType:'pkce'}});
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const say=(v,type='')=>{if(status){status.textContent=v;status.className='status '+type}};
let owner=false;
async function checkOwner(){
 if(!db)throw Error('Supabase is unavailable.');
 const {data,error}=await db.rpc('hte_my_role');
 if(error)throw error;
 owner=data==='owner';
 if(!owner)throw Error('Only the HTE owner can manage account roles.');
}
async function load(){
 list.innerHTML='<div class="empty">Loading private account roles…</div>';
 try{
  await checkOwner();
  const {data,error}=await db.rpc('hte_list_roles');
  if(error)throw error;
  const rows=Array.isArray(data)?data:[];
  list.innerHTML=rows.length?rows.map(r=>'<article class="hte-role-row"><div><strong>'+esc(r.email)+'</strong><small>'+esc(r.role.replaceAll('_',' '))+' · '+(r.is_active?'Active':'Inactive')+'</small></div><button class="btn '+(r.is_active?'danger':'gold')+'" type="button" data-role-email="'+esc(r.email)+'" data-role-value="'+esc(r.role)+'" data-role-active="'+(!r.is_active)+'">'+(r.is_active?'Deactivate':'Activate')+'</button></article>').join(''):'<div class="empty">No role assignments found.</div>';
 }catch(e){list.innerHTML='<div class="empty">Private account directory unavailable: '+esc(e.message)+'</div>';}
}
form.addEventListener('submit',async e=>{
 e.preventDefault();const button=form.querySelector('[type=submit]');button.disabled=true;
 try{
  await checkOwner();
  const email=form.elements.email.value.trim(),role=form.elements.role.value,active=form.elements.active.checked;
  if(!email||!['admin','music_promoter','partner'].includes(role))throw Error('Enter a registered email and select a valid role.');
  const {error}=await db.rpc('hte_assign_role_by_email',{_email:email,_role:role,_active:active});
  if(error)throw error;
  say('Account role saved. The user may need to sign out and sign in again.','success');
  form.reset();form.elements.active.checked=true;await load();
 }catch(e){say(e.message||'Could not save the account role.','error');}
 finally{button.disabled=false;}
});
list.addEventListener('click',async e=>{
 const button=e.target.closest('[data-role-email]');if(!button)return;button.disabled=true;
 try{
  await checkOwner();
  const {error}=await db.rpc('hte_assign_role_by_email',{_email:button.dataset.roleEmail,_role:button.dataset.roleValue,_active:button.dataset.roleActive==='true'});
  if(error)throw error;
  say('Account status updated.','success');await load();
 }catch(err){say(err.message||'Could not update this account.','error');}
 finally{button.disabled=false;}
});
$('#refresh-roles')?.addEventListener('click',load);
db?.auth.onAuthStateChange((_event,session)=>{if(session)setTimeout(load,0);else list.innerHTML='<div class="empty">Sign in with the HTE owner account to view private account roles.</div>';});
load();
})();