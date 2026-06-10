tasks.register("downloadIcon") {
    doLast {
        val url = java.net.URL("https://www.dropbox.com/scl/fi/bv4nl3pgwrp262zflp5jq/Picsart_26-06-10_23-08-28-724.png?rlkey=5ly47fxmc1mmskh7ibdqgwvnq&st=iydwwp4q&dl=1")
        url.openStream().use { input ->
            java.io.FileOutputStream("app/src/main/res/drawable/app_icon_downloaded.png").use { output ->
                input.copyTo(output)
            }
        }
    }
}
