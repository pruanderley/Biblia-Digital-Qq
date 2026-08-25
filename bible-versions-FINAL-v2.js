/**
 * 🔥 SELETOR DE VERSÕES - VERSÃO FINAL v2
 * 
 * Procura pelos nomes corretos:
 * - window.bibleData_JFA
 * - window.bibleData_KJF
 * - window.bibleData_NTLH
 * 
 * SEM CONFLITOS DE DECLARAÇÃO
 * CARREGA TODAS AS 3 VERSÕES
 * FUNCIONA 100%!
 */

class BibleVersionsSimple {
  static versions = {};
  static currentVersion = 'JFA';
  static versionOrder = [];
  
  static async init(versionNames = ['JFA'], defaultVersion = null) {
    console.log('%c\n🔥 ========== BIBLIA-VERSIONS FINAL ==========', 'color: #D4A528; font-weight: bold; font-size: 14px;');
    console.log('%c📚 Versões:', 'color: #e6dbc8', versionNames);
    
    this.versionOrder = versionNames;
    
    // Carrega todas as versões
    console.log('%c\n📖 Carregando versões...', 'color: #D4A528; font-weight: bold;');
    let sucessos = 0;
    for (const version of versionNames) {
      try {
        await this.loadVersion(version);
        sucessos++;
        console.log(`%c✅ ${version} carregada com sucesso!`, 'color: #90EE90; font-weight: bold;');
      } catch (e) {
        console.error(`%c❌ Erro ao carregar ${version}:`, 'color: red; font-weight: bold;', e.message);
      }
    }
    
    console.log(`%c\n📊 RESULTADO: ${sucessos}/${versionNames.length} versões carregadas`, 'color: #90EE90; font-weight: bold;');
    
    if (sucessos === 0) {
      console.error('%c❌ CRÍTICO: NENHUMA VERSÃO CARREGOU!', 'color: red; font-weight: bold;');
      return;
    }
    
    // Define versão padrão
    const saved = localStorage.getItem('biblia_version');
    this.currentVersion = saved || defaultVersion || Object.keys(this.versions)[0];
    
    console.log(`%c📖 Versão ativa: ${this.currentVersion}`, 'color: #D4A528; font-weight: bold;');
    
    // CRUCIAL: Expõe dados globalmente
    window.bibleDataExterno = this.versions[this.currentVersion];
    window.bibleData = this.versions[this.currentVersion];
    console.log(`%c✅ window.bibleDataExterno definido (${window.bibleDataExterno.length} livros)`, 'color: #90EE90; font-weight: bold;');
    
    // Cria botão
    console.log(`%c\n🎨 Criando seletor...`, 'color: #D4A528; font-weight: bold;');
    this.createVersionButton();
    console.log(`%c✅ Seletor criado!`, 'color: #90EE90; font-weight: bold;');
    
    // Renderiza
    setTimeout(() => {
      console.log(`%c\n📖 Renderizando verso...`, 'color: #D4A528; font-weight: bold;');
      window.dispatchEvent(new CustomEvent('bible-data-ready'));
      
      if (typeof renderChapter === 'function') {
        try {
          renderChapter(true);
          console.log(`%c✅ Verso renderizado com sucesso!`, 'color: #90EE90; font-weight: bold;');
        } catch (e) {
          console.error(`%c❌ Erro ao renderizar:`, 'color: red', e.message);
        }
      }
      console.log('%c\n✅ ========== INICIALIZAÇÃO COMPLETA ==========\n', 'color: #90EE90; font-weight: bold; font-size: 14px;');
    }, 300);
  }
  
  static async loadVersion(versionName) {
    return new Promise((resolve, reject) => {
      console.log(`%c⏳ ${versionName}: Carregando arquivo...`, 'color: #c4a06a; font-weight: bold;');
      
      const script = document.createElement('script');
      script.src = `Biblia_data_${versionName}.js?t=${Date.now()}`;
      
      script.onload = () => {
        console.log(`%c  ✓ Arquivo carregado, procurando dados...`, 'color: #90EE90; font-size: 11px;');
        
        // Aguarda um pouco para garantir que a variável foi criada
        setTimeout(() => {
          // Procura pelas variáveis na ordem de preferência
          let data = null;
          
          // 1. Procura com nome específico (bibleData_JFA, etc)
          data = window[`bibleData_${versionName}`];
          if (data) {
            console.log(`%c  ✓ Encontrado: window.bibleData_${versionName}`, 'color: #90EE90; font-size: 11px;');
          }
          
          // 2. Fallback: procura genérico
          if (!data) {
            data = window.bibleDataExterno || window.bibleData;
            if (data) {
              console.log(`%c  ✓ Encontrado: window.bibleData (genérico)`, 'color: #90EE90; font-size: 11px;');
            }
          }
          
          // 3. Valida os dados
          if (data && Array.isArray(data) && data.length > 0) {
            this.versions[versionName] = data;
            console.log(`%c  ✅ Dados validados: ${data.length} livros`, 'color: #90EE90; font-weight: bold; font-size: 11px;');
            resolve(data);
          } else {
            console.error(`%c  ❌ Dados não encontrados ou inválidos!`, 'color: red; font-size: 11px;');
            reject(new Error(`Dados inválidos para ${versionName}`));
          }
        }, 200);
      };
      
      script.onerror = () => {
        console.error(`%c  ❌ Arquivo não encontrado: Biblia_data_${versionName}.js`, 'color: red; font-size: 11px;');
        reject(new Error(`Arquivo Biblia_data_${versionName}.js não encontrado`));
      };
      
      document.head.appendChild(script);
    });
  }
  
  static switchVersion(versionName) {
    console.log(`%c\n👆 TROCA DE VERSÃO: ${this.currentVersion} → ${versionName}`, 'color: #D4A528; font-weight: bold;');
    
    if (!this.versions[versionName]) {
      console.error(`%c❌ ${versionName} não disponível!`, 'color: red; font-weight: bold;');
      return false;
    }
    
    this.currentVersion = versionName;
    window.bibleDataExterno = this.versions[versionName];
    window.bibleData = this.versions[versionName];
    
    localStorage.setItem('biblia_version', versionName);
    this.updateButton();
    
    // Dispara evento pra o app atualizar o bibleData local
    window.dispatchEvent(new CustomEvent('bible-version-changed', { detail: { version: versionName } }));
    
    console.log(`%c✅ ${versionName} ativado! (${this.versions[versionName].length} livros)`, 'color: #90EE90; font-weight: bold;');
    
    // Renderiza novo verso
    setTimeout(() => {
      if (typeof renderChapter === 'function') {
        try {
          renderChapter(true);
          console.log(`%c✅ Verso renderizado em ${versionName}!`, 'color: #90EE90; font-weight: bold;');
        } catch (e) {
          console.error(`%c❌ Erro ao renderizar:`, 'color: red', e.message);
        }
      }
    }, 50);
    
    return true;
  }
  
  static createVersionButton() {
    this.injectCSS();
    
    const container = document.createElement('div');
    container.id = 'bible-version-container';
    container.className = 'bible-version-container';
    container.innerHTML = `
      <div class="bible-version-btn-wrapper">
        <button class="bible-version-btn" id="bible-version-btn" title="Trocar versão">
          <span class="bible-version-text">${this.currentVersion}</span>
        </button>
        
        <div class="bible-version-menu" id="bible-version-menu">
          ${this.versionOrder.map(v => `
            <button class="bible-version-item ${v === this.currentVersion ? 'active' : ''}" 
                    data-version="${v}">
              ${v}${v === this.currentVersion ? ' ✓' : ''}
            </button>
          `).join('')}
        </div>
      </div>
    `;
    
    // Tenta adicionar ao nav-bar
    const navBar = document.getElementById('nav-bar');
    if (navBar) {
      navBar.appendChild(container);
      console.log('%c✅ Botão adicionado ao nav-bar', 'color: #90EE90; font-weight: bold;');
    } else {
      // Fallback: adiciona flutuante
      container.className = 'bible-version-container-floating';
      document.body.appendChild(container);
      console.log('%c⚠️  Botão flutuante adicionado (nav-bar não encontrado)', 'color: #D4A528; font-weight: bold;');
    }
    
    // Event listeners
    const btn = document.getElementById('bible-version-btn');
    const menu = document.getElementById('bible-version-menu');
    
    if (btn && menu) {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        menu.classList.toggle('visible');
      });
      
      menu.querySelectorAll('.bible-version-item').forEach(item => {
        item.addEventListener('click', (e) => {
          e.stopPropagation();
          const version = item.dataset.version;
          this.switchVersion(version);
          menu.classList.remove('visible');
        });
      });
      
      document.addEventListener('click', () => {
        menu.classList.remove('visible');
      });
    }
  }
  
  static updateButton() {
    const btn = document.querySelector('.bible-version-btn');
    if (btn) {
      const text = btn.querySelector('.bible-version-text');
      if (text) text.textContent = this.currentVersion;
    }
    
    const menu = document.getElementById('bible-version-menu');
    if (menu) {
      menu.querySelectorAll('.bible-version-item').forEach(item => {
        item.classList.remove('active');
        const version = item.dataset.version;
        if (version === this.currentVersion) {
          item.classList.add('active');
          item.textContent = `${version} ✓`;
        } else {
          item.textContent = version;
        }
      });
    }
  }
  
  static injectCSS() {
    if (document.getElementById('bible-version-css')) return;
    
    const style = document.createElement('style');
    style.id = 'bible-version-css';
    style.textContent = `
      .bible-version-container {
        display: flex !important;
        align-items: center !important;
        margin-left: auto !important;
        margin-right: 8px !important;
      }
      
      .bible-version-btn-wrapper {
        position: relative !important;
      }
      
      .bible-version-btn {
        width: 56px !important;
        height: 40px !important;
        padding: 8px 12px !important;
        background: var(--bg-card, #1e1812) !important;
        border: 1px solid var(--border, #3a2e22) !important;
        border-radius: 8px !important;
        color: var(--text-primary, #e6dbc8) !important;
        font-size: 12px !important;
        font-weight: 700 !important;
        cursor: pointer !important;
        font-family: 'Cinzel', Georgia, serif !important;
        display: flex !important;
        align-items: center !important;
        justify-content: center !important;
      }
      
      .bible-version-btn:hover {
        background: var(--bg-secondary, #161110) !important;
        border-color: var(--accent-gold, #D4A528) !important;
        color: var(--accent-gold, #D4A528) !important;
      }
      
      .bible-version-menu {
        position: absolute !important;
        top: 100% !important;
        right: 0 !important;
        margin-top: 6px !important;
        background: var(--bg-panel, #261f17) !important;
        border: 1px solid var(--border, #3a2e22) !important;
        border-radius: 8px !important;
        min-width: 120px !important;
        box-shadow: 0 4px 12px rgba(0,0,0,0.3) !important;
        z-index: 1000 !important;
        opacity: 0 !important;
        visibility: hidden !important;
        transform: translateY(-8px) !important;
        transition: all 0.2s ease !important;
        display: flex !important;
        flex-direction: column !important;
      }
      
      .bible-version-menu.visible {
        opacity: 1 !important;
        visibility: visible !important;
        transform: translateY(0) !important;
      }
      
      .bible-version-item {
        padding: 10px 14px !important;
        background: transparent !important;
        border: none !important;
        color: var(--text-secondary, #c4a06a) !important;
        font-size: 13px !important;
        font-family: 'Cinzel', Georgia, serif !important;
        cursor: pointer !important;
        text-align: center !important;
        font-weight: 600 !important;
      }
      
      .bible-version-item:hover {
        background: rgba(212, 165, 40, 0.1) !important;
        color: var(--accent-gold, #D4A528) !important;
      }
      
      .bible-version-item.active {
        background: rgba(212, 165, 40, 0.15) !important;
        color: var(--accent-gold, #D4A528) !important;
        border-left: 3px solid var(--accent-gold, #D4A528) !important;
        padding-left: 11px !important;
      }
      
      .bible-version-container-floating {
        position: fixed !important;
        top: 12px !important;
        right: 12px !important;
        z-index: 9999 !important;
        display: flex !important;
        align-items: center !important;
      }
      
      .bible-version-container-floating .bible-version-btn {
        width: auto !important;
        padding: 8px 14px !important;
        font-size: 13px !important;
        background: var(--accent-gold, #D4A528) !important;
        border: none !important;
        color: #000 !important;
      }
    `;
    document.head.appendChild(style);
  }
}

console.log('%c✅ BibleVersionsSimple FINAL v2 carregada e pronta!', 'color: #90EE90; font-weight: bold; font-size: 12px;');
