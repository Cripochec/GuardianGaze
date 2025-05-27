document.addEventListener('DOMContentLoaded', () => {

  // =========================
  // БЛОК: Уведомление "Нужна помощь?"
  // =========================
  const helpBtn = document.querySelector('.help-button');
  const notification = document.getElementById('notification');
  const errorMsg1 = document.getElementById('error-msg1');
  const errorMsg2 = document.getElementById('error-msg2');
  const errorMsg3 = document.getElementById('error-msg3');

  if (helpBtn) {
    helpBtn.addEventListener('click', () => {
      notification.style.display = 'block';
      setTimeout(() => {
        notification.style.display = 'none';
      }, 5000);
    });
  }


  // =========================
  // БЛОК: Смена фона каждые 10 секунд
  // =========================
  const images = [
    '/static/images/photo1.jpg',
    '/static/images/photo2.jpg',
    '/static/images/photo3.jpg',
    '/static/images/photo4.jpg',
    '/static/images/photo5.jpg',
    '/static/images/photo6.jpg'
  ];
  let currentIndex = 0;

  function setBackground() {
    document.body.style.backgroundImage = `url('${images[currentIndex]}')`;
  }

  function changeBackground() {
    currentIndex = (currentIndex + 1) % images.length;
    setBackground();
  }

  setBackground();
  setInterval(changeBackground, 10000); // Смена каждые 10 секунд

  // =========================
  // БЛОК: Открытие и закрытие модального окна для добавления водителя
  // =========================
  const addDriverBtn = document.getElementById('add-driver-btn');
  const modal = document.getElementById('modal');
  const closeModalBtn = document.getElementById('close-modal');

  if (addDriverBtn && modal) {
    addDriverBtn.addEventListener('click', () => {
      modal.classList.remove('hidden');
    });
  }

  if (closeModalBtn && modal) {
    closeModalBtn.addEventListener('click', () => {
      modal.classList.add('hidden');
    });
  }

});
