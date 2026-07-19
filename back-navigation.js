// ── CONTROLE DO BOTÃO VOLTAR DO ANDROID ──
(function() {

  function showBibliaExitDialog() {
    var ex = document.getElementById('biblia-exit-dialog');
    if (ex) { ex.remove(); return; }

    var d = document.createElement('div');
    d.id = 'biblia-exit-dialog';
    d.style.cssText = 'position:fixed;inset:0;z-index:9999;display:flex;' +
      'align-items:center;justify-content:center;background:rgba(0,0,0,0.75);';
    d.innerHTML =
      '<div style="background:#1a1008;border:1px solid #C8962D;border-radius:20px;' +
      'padding:32px 24px;max-width:300px;width:88%;text-align:center;">' +
        '<div style="font-size:32px;margin-bottom:10px;">📖</div>' +
        '<div style="font-family:Cinzel,serif;font-size:15px;color:#C8962D;' +
        'letter-spacing:1px;margin-bottom:24px;">O QUE DESEJA FAZER?</div>' +
        '<button id="biblia-exit-btn" ' +
        'style="width:100%;padding:15px;border-radius:12px;border:none;margin-bottom:14px;' +
        'background:linear-gradient(135deg,#C8962D,#e6b84a);color:#0d0a08;' +
        'font-family:Cinzel,serif;font-size:14px;font-weight:700;cursor:pointer;">' +
        '🚪 Encerrar Aplicativo</button>' +
        '<button id="biblia-cancel-btn" ' +
        'style="width:100%;padding:13px;border-radius:12px;border:none;' +
        'background:rgba(255,255,255,0.08);color:#C8962D;border:1px solid #C8962D;' +
        'font-family:Cinzel,serif;font-size:14px;cursor:pointer;">Cancelar</button>' +
      '</div>';
    document.body.appendChild(d);

    document.getElementById('biblia-exit-btn').addEventListener('click', function() {
      document.getElementById('biblia-exit-dialog').remove();
      // Retorna false para o onBackPressed fechar o app
      window._shouldExit = true;
      if (typeof window.Android !== 'undefined' && typeof window.Android.exitApp === 'function') {
        window.Android.exitApp();
      } else {
        finishAffinity(); // fallback
      }
    });
    document.getElementById('biblia-cancel-btn').addEventListener('click', function() {
      document.getElementById('biblia-exit-dialog').remove();
    });
  }

  function handleAndroidBackButton() {
    // Fecha diálogo se aberto
    var exitDialog = document.getElementById('biblia-exit-dialog');
    if (exitDialog) { exitDialog.remove(); return true; }

    var hpDialog = document.getElementById('hp-exit-dialog');
    if (hpDialog) { hpDialog.remove(); return true; }

    // 1. Harpa aberta
    var harpaApp = document.getElementById('harpa-app');
    if (harpaApp && harpaApp.classList.contains('open')) {
      if (typeof hp_navBack === 'function') {
        hp_navBack();
        return true;
      }
    }

    // 2. Painel aberto
    var openPanels = document.querySelectorAll('.panel-overlay.open');
    if (openPanels.length > 0) {
      var lastPanel = openPanels[openPanels.length - 1];
      if (lastPanel && lastPanel.id) {
        if (typeof closePanel === 'function') closePanel(lastPanel.id);
        return true;
      }
    }

    // 3. Mostra diálogo — não fecha ainda
    showBibliaExitDialog();
    return true; // mantém app aberto até usuário confirmar
  }

  // Capacitor
  if (typeof Capacitor !== 'undefined' && Capacitor.Plugins && Capacitor.Plugins.App) {
    Capacitor.Plugins.App.addListener('backButton', function() {
      handleAndroidBackButton();
    });
  }

  // Fallback
  document.addEventListener('backbutton', function(e) {
    e.preventDefault();
    handleAndroidBackButton();
  }, false);

  // Para MainActivity.java — retorna false para fechar o app
  window.handleBack = function() {
    return handleAndroidBackButton();
  };

  console.log('✅ Controle de Voltar ativado!');
})();
