/* Shared city selection. IP results are approximate and never default to Hangzhou. */
(() => {
  const key='lifepulse.city.v1';
  let state={city:'',source:'unavailable',located:false};
  try { const saved=JSON.parse(localStorage.getItem(key));if(saved&&(saved.source==='manual'||Date.now()-saved.savedAt<1800000))state=saved; }catch(e){}
  const api={get city(){return state.city||''},get state(){return state},ready:null,open};
  window.LifePulseLocation=api;
  function publish(next){state=next;try{localStorage.setItem(key,JSON.stringify({...next,savedAt:Date.now()}))}catch(e){}render();window.dispatchEvent(new CustomEvent('lifepulse:city-change',{detail:state}));return state;}
  async function locate(){const abort=new AbortController();const timer=setTimeout(()=>abort.abort(),9000);try{const response=await fetch('/api/location/current',{signal:abort.signal});const result=await response.json();if(!response.ok||!result.success)throw Error();return publish(result.data);}catch(e){return publish({city:'',source:'unavailable',located:false,notice:'暂时无法定位，请手动选择城市。'});}finally{clearTimeout(timer)}}
  api.ready=state.city?Promise.resolve(state):locate();
  function render(){document.querySelectorAll('.city-btn,.city-select,[data-location-city]').forEach(el=>{el.textContent=(state.city||'选择城市')+' ⌄';el.title=state.source==='manual'?'手动选择的城市，点击切换':'根据IP近似定位，点击切换';el.setAttribute('role','button');el.tabIndex=0;el.onclick=open;el.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();open()}};});}
  function open(){let dialog=document.getElementById('lp-city-dialog');if(!dialog){dialog=document.createElement('dialog');dialog.id='lp-city-dialog';dialog.className='lp-city-dialog';
      const title=document.createElement('h2');title.textContent='选择你的城市';const note=document.createElement('p');note.textContent='IP定位可能受网络代理影响。可选择城市或输入城市名称。';
      const options=document.createElement('div');options.className='lp-city-options';['北京','上海','广州','深圳','杭州','成都','重庆','武汉','南京','苏州','西安','天津','郑州','长沙','青岛','宁波'].forEach(city=>{const b=document.createElement('button');b.type='button';b.textContent=city;b.onclick=()=>select(city);options.append(b)});
      const form=document.createElement('form');const input=document.createElement('input');input.id='lp-city-input';input.placeholder='其他城市，例如：合肥';input.maxLength=40;input.setAttribute('aria-label','城市名称');const submit=document.createElement('button');submit.textContent='确认';form.append(input,submit);form.onsubmit=e=>{e.preventDefault();const city=input.value.trim().replace(/市$/,'');if(city)select(city)};
      const actions=document.createElement('div');actions.className='lp-city-actions';const retry=document.createElement('button');retry.textContent='重新IP定位';retry.onclick=async()=>{retry.disabled=true;retry.textContent='正在定位…';await locate();retry.disabled=false;retry.textContent='重新IP定位';dialog.close()};const close=document.createElement('button');close.textContent='关闭';close.onclick=()=>dialog.close();actions.append(retry,close);dialog.append(title,note,options,form,actions);document.body.append(dialog);
      function select(city){publish({city,source:'manual',located:true});dialog.close()}
    }dialog.showModal();}
  window.addEventListener('lifepulse:city-render',render);
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',render);else render();
})();
