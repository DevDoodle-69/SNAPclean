const https = require('https');
const fs = require('fs');

const file = fs.createWriteStream("app/src/main/res/drawable/ic_launcher_foreground_dl.png");
https.get("https://www.dropbox.com/scl/fi/bv4nl3pgwrp262zflp5jq/Picsart_26-06-10_23-08-28-724.png?rlkey=5ly47fxmc1mmskh7ibdqgwvnq&st=iydwwp4q&dl=1", function(response) {
  if (response.statusCode === 302 && response.headers.location) {
     https.get(response.headers.location, function(resp) {
         resp.pipe(file);
     });
  } else {
     response.pipe(file);
  }
});
