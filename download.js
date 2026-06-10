const https = require('https');
const fs = require('fs');

const url = 'https://www.dropbox.com/scl/fi/4v7k1oqyeirqt4tl7pube/u_u4pf5h7zip-woosh-345977.mp3?rlkey=8iqjha5vnd7xpfip9oc296m6l&st=jv8ufmk8&dl=1';
const dest = 'app/src/main/res/raw/woosh.mp3';

fs.mkdirSync('app/src/main/res/raw', { recursive: true });

https.get(url, (res) => {
    if (res.statusCode === 301 || res.statusCode === 302) {
        https.get(res.headers.location, (res2) => {
            if (res2.statusCode === 301 || res2.statusCode === 302) {
                https.get(res2.headers.location, (res3) => {
                    const file = fs.createWriteStream(dest);
                    res3.pipe(file);
                    file.on('finish', () => {
                        file.close();
                        console.log('Download complete');
                    });
                });
            } else {
                const file = fs.createWriteStream(dest);
                res2.pipe(file);
                file.on('finish', () => {
                    file.close();
                    console.log('Download complete');
                });
            }
        });
    } else {
        const file = fs.createWriteStream(dest);
        res.pipe(file);
        file.on('finish', () => {
            file.close();
            console.log('Download complete');
        });
    }
}).on('error', (err) => {
    console.error('Error downloading:', err.message);
});
