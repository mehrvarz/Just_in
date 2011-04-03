// @author timur.mehrvarz@web.de

var messageMaxHeight;       // from getMaxRenderHeight()
var columns = 1;
var lastInnerWidth = -1;
var resizeAllowed = false;
var msgElement = [];        // we keep array's of the kept msgElement[] and msgElementHeight[] in the DOM, so we can do a quick re-layout in resize() and checkReachedMaxMessages()
var msgElementHeight = [];
var colHeight;
var columnElement;
var msgTitleArray = [];
var idNumber=0, idDeletedCount=0;
var msgInARow=0, maxMsgInARow=30; // used for display delay of incoming msgs
var msgInARowElements = [];
var wrapElement;
var dynamicElement;
var newMsgElement = 0;

window.addEventListener("load",onload,false); // call function onload() when document is loaded

function onload() {
  window.removeEventListener("load",onload,false);
  wrapElement = document.getElementById("wrap");
  dynamicElement = document.getElementById("dynamic");
  resizeAllowed = true;
  resize(); // run initial resize() to initialize render-presets

  if(!window.JsObject) {
    layoutStart();
    layoutNewMessage("Title 123", 
                     "Channel", 
                     "Hello, this is the description", 
                     "#000", 
                     "timur.mobi", 
                     "http://timur.mobi",
                     0,
                     null);
  } else {
    if(window.JsObject.getEntry()) { // wait for the next message
      //console.log("onload3() from getMessageEntry() title="+window.JsObject.getEntryTitle()+" channelName="+window.JsObject.getEntryChannel());
      //console.log("onload3() title="+window.JsObject.getEntryTitle()+" channelName="+window.JsObject.getEntryChannel()+" messageMaxHeight="+messageMaxHeight);
      messageMaxHeight = window.JsObject.getMaxRenderHeight();

      layoutStart();
      layoutNewMessage(window.JsObject.getEntryTitle(), 
                       window.JsObject.getEntryChannel(), 
                       window.JsObject.getEntryDescription(), 
                       "#000", // background color
                       "", // channel name
                       window.JsObject.getEntryLink(), 
                       window.JsObject.getEntryTime(),
                       window.JsObject.getEntryImageUrl());
      window.JsObject.setRenderHeight(colHeight);
      // todo: must set this again, when all images were loaded?
    }
  }
}

function onImageLoad(img) {
  var width = img.width;
  var height = img.height;
  console.log("onImageLoad() width="+width+" height="+height+" newMsgElement.offsetHeight="+newMsgElement.offsetHeight+" ----");
  window.JsObject.setRenderHeight(newMsgElement.offsetHeight);
}

function layoutStart() {
  // called before a set of new messages will be added to dynamicElement
  msgInARow = 0;
}

function layoutNewMessage(title, contactname, description, bgcolor, provider, urlString, timeMs, imageUrl) {
  for(var i=0; i<idNumber; i++)
    if(msgTitleArray[i])
      if(msgTitleArray[i]==title) {
        console.log("layoutNewMessage() msgTitleArray["+i+"] EXIST title:"+title);
        return;
      } 

  idNumber++;
  msgTitleArray[idNumber]=title;

  //console.log("layoutNewMessage() title="+title+" contactname="+contactname); //+" description=["+description+"]");
  var msgDate = new Date(timeMs);
  var msgHours = "0"+msgDate.getHours();
  var msgMinutes = "0"+msgDate.getMinutes();
  var msgHourMinutesString = msgHours.substr(msgHours.length-2,2) +":"+ msgMinutes.substr(msgMinutes.length-2,2);

  if(description=="n/a"    // as being sent by channel 'Welt'    // TODO: this is from ajax.js:getElementTextNS()
  || description=="null"
  || description=="paging_filter"
  )
    description=null;

  newMsgElement = document.createElement("div");
  newMsgElement.setAttribute("class","message");
  var newId = "msg"+idNumber;
  newMsgElement.setAttribute("id",newId);
  newMsgElement.setAttribute("style","max-height:"+messageMaxHeight+"px;");     // todo: unfortunately this cuts through the last text-line

  var insertMarkup = "";
  if(urlString) {
    //console.log("layoutNewMessage() with urlString, idNumber="+idNumber+" title="+window.JsObject.getEntryTitle()+" channelName="+window.JsObject.getEntryChannel());
    var domainString;
    var iconUrl;
    var iconClass;

    if(imageUrl) {
      iconUrl = imageUrl;
      iconClass = "icon";
      //console.log("layoutNewMessage() iconUrl="+iconUrl);
    }
    else {
      domaintring = urlString.substring(7);

      if(domainString=="n/a")
        domainString="";

      if(domainString) {
        // TODO: always remove any subdomain?
        if(domainString.startsWith("go."))
          domainString = domainString.substring(3);
        if(domainString.startsWith("rss."))
          domainString = domainString.substring(4);
        if(domainString.startsWith("news."))
          domainString = domainString.substring(5);
        if(domainString.startsWith("feeds."))
          domainString = domainString.substring(6);

        var firstSlash = domainString.indexOf("/");
        if(firstSlash>0)
          domainString = domainString.substring(0,firstSlash);
      }

      if(contactname=="Focus") domainString="www.focus.de";
      if(contactname=="heute") domainString="heute.de";
      if(contactname=="telepolis") domainString="www.heise.de/tp";
      if(contactname=="Gizmodo") domainString="gizmodo.com";
      if(contactname=="BBC World") domainString="bbc.co.uk";
      if(contactname=="BoingBoing") domainString="boingboing.com";

      domainString = urlString.substring(0,7)+domainString;
      iconUrl = domainString+"/favicon.ico";
      iconClass = "favico";
    }

    if(title.indexOf("<a")>=0) {
      insertMarkup += "<h3>"+contactname+" "+msgHourMinutesString+"</h3>"+
                      "<p><img class='"+iconClass+"' src='"+iconUrl+"'/>"+title+"</p>";
    }
    else {
      insertMarkup += "<h3>"+contactname+" "+msgHourMinutesString+"</h3>"+
                      "<a href='"+xmlEncode(urlString)+"'><h2><img class='"+iconClass+"' src='"+iconUrl+"'/>"+title+"</h2></a>";
    }
  } else {
    //console.log("layoutNewMessage() no urlString, idNumber="+idNumber+" title="+window.JsObject.getEntryTitle()+" channelName="+window.JsObject.getEntryChannel());
    insertMarkup += "<h3>"+contactname+" "+msgHourMinutesString+"</h3><h2>";
    if(imageUrl!=null)
      insertMarkup += "<img class='icon' src='"+imageUrl+"'/>";
    insertMarkup += ""+title+"</h2>";
  }

  if(description) {
    description = adblockHtml(description,contactname); // SPECIAL ADBLOCK FOR WEBKIT BROWSERS: destroy ad domain names
    insertMarkup += "<p style='clear:both:display:block;margin-left:8px'>"+description+"</p>";
  }

  if(!description) // mainly for twitter msgs: enlarging text size of short messages
    insertMarkup = "<div style='line-height:120%;font-size:120%;'>"+insertMarkup+"<p><br/>&nbsp;</p></div>";

  newMsgElement.innerHTML = insertMarkup;

  columnElement.appendChild(newMsgElement);
  // the new message is now in the DOM (as child of columnId[smallestId])
  msgElement[idNumber] = newMsgElement;
  adblockElement(newMsgElement,contactname); // adblock for all browsers

  // after all adblocking, we got the final offsetHeight of the message
  msgElementHeight[idNumber] = newMsgElement.offsetHeight;

  // calculate the height of the column the msg was put into (needed in addMsgToSmallestColumn())
  colHeight += msgElementHeight[idNumber];
  //console.log("layoutNewMessage() msgElementHeight["+idNumber+"]="+msgElementHeight[idNumber]+" colHeight="+colHeight);

  // keep a reference of the newMsgElement for applying the css animation class
  msgInARowElements[msgInARow++] = newMsgElement;
}

function resize(init) {
  if(!resizeAllowed) {
    console.log("resize("+init+") resizeAllowed="+resizeAllowed+" ABORT");
    return;
  }

  if(window.innerWidth==lastInnerWidth)
    return; // no need to re-layout

  /////////// re-layout

  //console.log("resize() start ...");
  var startMs = new Date().getTime();
  columns=1;
    
  //console.log("resize() columns="+columns+" window.innerWidth="+window.innerWidth+"("+lastInnerWidth+") messageMaxHeight="+messageMaxHeight);
  lastInnerWidth = window.innerWidth;

  // rename old dynamicElement tree
  var oldDynamicElement = dynamicElement;
  oldDynamicElement.id = "dynamicOld";

  // create new dynamicElement tree with base structure
  dynamicElement = document.createElement("div");
  dynamicElement.setAttribute("id","dynamic");
  wrapElement.insertBefore(dynamicElement,oldDynamicElement);

  columnElement = document.createElement("div");
  columnElement.setAttribute("class","column");
  columnElement.setAttribute("id","col1");
  dynamicElement.appendChild(columnElement);
  colHeight=0;

  // move all msg-objects to columns in new dynamicElement
  if(idNumber>0) {
    var oldIdNumber = idNumber, obj;
    for(idNumber=idDeletedCount+1; idNumber<=oldIdNumber; idNumber++) {
      obj = msgElement[idNumber];
      if(obj)
        columnElement.appendChild(obj);
        // the new message is now in the DOM (as child of columnId[smallestId])
    }
  }

  // remove old dynamicElement
  wrapElement.removeChild(oldDynamicElement);
}

