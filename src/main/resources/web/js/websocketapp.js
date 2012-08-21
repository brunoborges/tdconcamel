var index = 0;
$(window).keydown(function (event) {
    if (event.which == 27) {
        // the following seems to fix the symptom but only in case the document has the focus
        // prevents websocket ESC bug
        event.preventDefault();
    }
});

$(document).ready(function () {
    if (!window.WebSocket) {
        $('#pictures').text("ERROR: Your browser doesn't support websockets!");
    } else {
        appImages.start();
        appStatistics.start();
    }
});

function updateImage(tweet) {
    var localIndex = 1;
    if (index >= 12) {
        index = 1;
    } else {
        index++;
        localIndex = index;
    }

    $('#' + localIndex + ' a').addClass('loading');
    var imageUrl = tweet.url + ':thumb';
    var image = $('<img />')
    .load(function () {
        $('#' + localIndex).html('<a title="@' + tweet.name + ': ' + tweet.text + '" rel="lightbox" href="' + tweet.url + '"><img src="' + imageUrl + '" /></a>');
        $('#' + localIndex + ' a').removeClass('loading');
    })
    .error(function () {
        $('#' + localIndex + ' a').removeClass('loading');
    //console.log("error loading " + imageUrl);
    })

    .attr('src', imageUrl);
}

function setStats(data) {
    var statistics = jQuery.parseJSON(data);
    $('#tweetCount').text(statistics.tweetCount);
    $('#imageCount').text(statistics.imageCount);
}

function gallery(data) {
    var tweet = jQuery.parseJSON(data);
    updateImage(tweet);
}

var appImages = {
    start:function () {
        var location = "ws://localhost:9292/tdconcamel/images";
        this._ws = new WebSocket(location);
        this._ws.onmessage = this._onmessage;
        this._ws.onclose = this._onclose;
    },

    _onmessage:function (m) {
        if (m.data) {
            gallery(m.data);
        }
    },

    _onclose:function (m) {
        this._ws = null;
    }
};
    
var appStatistics = {
    clearStatistics:function() {
        if (this._ws) {
            this._ws.send("clear");
        }
    },

    start:function () {
        var location = "ws://localhost:9292/tdconcamel/statistics";
        this._ws = new WebSocket(location);
        this._ws.onmessage = this._onmessage;
        this._ws.onclose = this._onclose;
    },

    _onmessage:function (m) {
        if (m.data) {
            setStats(m.data);
            startUptimeCounter(m.data);
        }
    },

    _onclose:function (m) {
        this._ws = null;
    }
};

/**
 * UPTIME
 */
var startedOn = 0;
var uptime = 0;
var intervalId = 0;

function startUptimeCounter(data) {
    if (startedOn > 0) {
        return;
    }

    var statistics = jQuery.parseJSON(data);
    startedOn = statistics.startedOn;
    uptime = new Date().getTime();
    intervalId = setInterval(refreshUptime, 1000);
}

function refreshUptime() {
    uptime = uptime + 1000;
    var difference = uptime - startedOn;
    var daysDifference = Math.floor(difference/1000/60/60/24);
    difference -= daysDifference*1000*60*60*24;
    var hoursDifference = Math.floor(difference/1000/60/60);
    difference -= hoursDifference*1000*60*60;
    var minutesDifference = Math.floor(difference/1000/60);
    difference -= minutesDifference*1000*60
    var secondsDifference = Math.floor(difference/1000);
 
    $("#uptime").text(daysDifference + "d "+ hoursDifference+":"+minutesDifference+":"+secondsDifference);
}

