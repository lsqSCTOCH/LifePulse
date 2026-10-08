/* Brand navigation sits outside each existing Vue application. */
(() => {
  const current = location.pathname.split('/').pop() || 'index.html';
  const links = [
    ['index.html', '⌂', '发现生活', 'shop-list.html', 'shop-detail.html', 'blog-detail.html'],
    ['customer-service.html', '✦', '智能客服'],
    ['blog-edit.html', '＋', '分享探店'],
    ['info.html', '○', '个人中心', 'info-edit.html', 'other-info.html', 'login.html', 'login2.html']
  ];
  const header = document.createElement('div');
  header.className = 'lp-brandbar';
  header.innerHTML = '<a class="lp-brand" href="./index.html"><img src="./imgs/lifepulse-mark.svg" alt=""><span><strong>LifePulse</strong><small>智享生活平台</small></span></a><div class="lp-brand-motto">发现城市里的每一种美好<span>LIVE A LITTLE MORE.</span></div><button class="lp-account lp-global-city" data-location-city>选择城市</button><a class="lp-account" href="./info.html">我的生活 <span>↗</span></a>';
  const sidebar = document.createElement('aside');
  sidebar.className = 'lp-sidebar';
  const nav = document.createElement('nav');
  nav.setAttribute('aria-label', '平台导航');
  links.forEach(([url, icon, text, ...related]) => {
    const a = document.createElement('a');
    a.href = './' + url;
    if (current === url || related.includes(current)) {
      a.className = 'is-current';
      a.setAttribute('aria-current', 'page');
    }
    const span = document.createElement('span'); span.textContent = icon; span.setAttribute('aria-hidden', 'true');
    a.append(span, document.createTextNode(text)); nav.append(a);
  });
  sidebar.append(nav);
  const note = document.createElement('div'); note.className = 'lp-sidebar-note';
  note.innerHTML = '<span>✦</span><strong>让生活，多一点灵感</strong><p>一家好店，一次分享。<br>把日常过成喜欢的样子。</p><a href="./customer-service.html">和客服聊聊 →</a>';
  sidebar.append(note);
  document.body.prepend(header, sidebar);
  LifePulseLocation.ready.then(() => window.dispatchEvent(new Event('lifepulse:city-render')));
})();
