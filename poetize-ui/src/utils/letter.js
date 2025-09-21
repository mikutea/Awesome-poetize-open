var aparted = false;

// 使用原生 JavaScript 替代 jQuery
document.addEventListener('DOMContentLoaded', function() {
  const openElement = document.getElementById('open');
  if (openElement) {
    openElement.addEventListener('click', function () {
      if (!aparted) {
        var typed = new Typed('.letter', {
          strings: ["^1000Dear&nbsp;&nbsp;Ming",
            "M^200ing<br><br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;夜には^300いつも^200寒いよね、^600でも、^600手を^200繋いでいると、^600暖か^200くなるよ！<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;どんなに^300寒い夜も、^600君と^300二人でいれば、^600ちっとも^300寒くない！<br><br><p style='float:right; display:block; width:80px;'>^1000H^200ao</p>"],
          typeSpeed: 100,
          backSpeed: 50
        });

        const openSpan = openElement.querySelector('span');
        if (openSpan) {
          openSpan.style.backgroundPosition = '0 -150px';
        }

        aparted = true;

        var music = document.getElementById('music2');
        if (music && music.paused) {
          music.play();
          const musicBtn = document.getElementById('music_btn2');
          if (musicBtn) {
            musicBtn.style.opacity = '1';
          }
        }
      }
    });
  }
});

function playPause() {
  var music = document.getElementById('music2');
  var music_btn = document.getElementById('music_btn2');

  if (music && music_btn) {
    if (music.paused) {
      music.play();
      music_btn.style.opacity = '1';
    } else {
      music.pause();
      music_btn.style.opacity = '0.2';
    }
  }
}

window.onload = function () {
  var currentUrl = window.location.href;
  var firstIndex = currentUrl.indexOf("#");
  if (firstIndex <= 0) window.location.href = currentUrl + "#contact";

  const music2Element = document.getElementById('music2');
  if (music2Element && typeof bgmsrc !== 'undefined') {
    music2Element.src = bgmsrc;
  }

  document.addEventListener('touchstart', function (event) {
    if (event.touches.length > 1) event.preventDefault();
  });

  var lastTouchEnd = 0;

  document.addEventListener('touchend', function (event) {
    var now = (new Date()).getTime();
    if (now - lastTouchEnd <= 300) event.preventDefault();
    lastTouchEnd = now;
  }, false);

  document.addEventListener('gesturestart', function (event) {
    event.preventDefault();
  });

  // 使用原生 JavaScript 替代 jQuery
  document.body.style.opacity = '1';
  
  const cherryContainer = document.getElementById('jsi-cherry-container');
  if (cherryContainer) {
    cherryContainer.style.zIndex = '-99';
  }
}
