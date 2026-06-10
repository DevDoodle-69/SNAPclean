const fs = require('fs');
const path = require('path');

const src = 'app/src/main/res/drawable/app_icon.png';
const dirs = ['mipmap-mdpi', 'mipmap-hdpi', 'mipmap-xhdpi', 'mipmap-xxhdpi', 'mipmap-xxxhdpi'];

dirs.forEach(dir => {
    const targetDir = `app/src/main/res/${dir}`;
    if (!fs.existsSync(targetDir)) {
        fs.mkdirSync(targetDir, { recursive: true });
    }
    fs.copyFileSync(src, path.join(targetDir, 'ic_launcher.png'));
    fs.copyFileSync(src, path.join(targetDir, 'ic_launcher_round.png'));
});

console.log('Copy complete');
