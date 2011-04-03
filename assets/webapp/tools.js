String.prototype.startsWith = function(str) {return (this.match("^"+str)==str) }
String.prototype.endsWith   = function(str) {return this.length >= str.length && this.substr(this.length - str.length) == str;}

function debug(text) {
  if(window.JsObject) 
    window.JsObject.debug(text);                            // java console
  else
  if(this.console && typeof console.log != "undefined") 
    console.log(text);                                      // browser console
}

function xmlEncode(input) {
  var output="";
  for(var i=0; i<input.length; i++) {
    var c = input.charAt(i);
    if(c=='&')
      output+="&#38;";
    else
    if(c=='<')
      output+="&#60;";
    else
    if(c=='>')
      output+="&#62;";

    else
    if(c=='ß')
      output+="&#xDF;";
    else
    if(c=='ä')
      output+="&#xE4;";
    else
    if(c=='ö')
      output+="&#xF6;";
    else
    if(c=='ü')
      output+="&#xFC;";
    else
    if(c=='Ä')
      output+="&#xC4;";
    else
    if(c=='Ö')
      output+="&#xD6;";
    else
    if(c=='Ü')
      output+="&#xDC;";

    else
    if(c=='\"')
      output+="&#34;";
    else
    if(c=='\'')
      output+="&#39;";

    else
      output+=c;
  }
  return output;
}

function strReplace(insertMarkup,searchStr,replaceStr)
{
  var idx;
  while((idx = insertMarkup.indexOf(searchStr))>=0)
    insertMarkup = insertMarkup.replace(searchStr,replaceStr);
  return insertMarkup;
}

function adblockHtml(markupStr,info)
{
  // SPECIAL ADBLOCK FOR WEBKIT BROWSERS: strip ad domains (before! DOM)

  markupStr = strReplace(markupStr,"feedburner.com/","localhost/");
  markupStr = strReplace(markupStr,"feedsportal.com/","localhost/");
  markupStr = strReplace(markupStr,"hits.guardian.co.uk/","localhost/");
  markupStr = strReplace(markupStr,"doubleclick.net/","localhost/");
  markupStr = strReplace(markupStr,"pheedo.com/","localhost/");
  markupStr = strReplace(markupStr,"pheedcontent.com/","localhost/");
  markupStr = strReplace(markupStr,"invitemedia.com/","localhost/");
  markupStr = strReplace(markupStr,"pixel.quantserve.com/","localhost/");
  markupStr = strReplace(markupStr,"feeds.gawker.com/","localhost/");
  markupStr = strReplace(markupStr,"feeds.macnn.com/","localhost/");
  markupStr = strReplace(markupStr,"hits.guardian.co.uk/","localhost/");

  //markupStr = strReplace(markupStr,"data:image/png;base64","");
  //markupStr = strReplace(markupStr,"segment-pixel.","selment-pisel.");
  markupStr = strReplace(markupStr,"<img src=\"http://feeds.localhost","<div alt=\"http://feeds.localhost");
  markupStr = strReplace(markupStr,"<img alt=\"\" height=\"0\" width=\"0\"","<div height=\"0\" width=\"0\"");

  // remove all xml comments
  var commentStart = markupStr.indexOf("<!--");
  while(commentStart>=0)
  {
    var commentEnd = markupStr.substring(commentStart+4).indexOf("-->");
    if(commentEnd>=0)
    {
      markupStr = markupStr.substring(0,commentStart) + markupStr.substring(commentStart+4+commentEnd+3);
    }
    commentStart = markupStr.indexOf("<!--");
  }

  //if(info=="Gizmodo")
  //  debug("markupStr=["+markupStr+"]"); 

  return markupStr;
}

function adblockElement(element,info)
{
  // DOM based adblock
  var allChildElements = element.getElementsByTagName('*');
  if(allChildElements)
  {
    var elementCount = allChildElements.length;
    for(var msgNr=0; msgNr<elementCount; msgNr++)
    {
      var adElement = allChildElements[msgNr];
      if(adElement)
      {
        var imgUrl;
        try {
          imgUrl = adElement.src;
        } catch(error) {}
        if(imgUrl)
        {
          if(imgUrl==""
          || imgUrl.indexOf("localhost/")>=0 || imgUrl.indexOf(".localhost")>=0
          || imgUrl.indexOf("feedsportal")>=0 
          || imgUrl.indexOf("feedburner.com")>=0
          || imgUrl.indexOf("doubleclick")>=0
          || imgUrl.indexOf("feedads")>=0
          || imgUrl.indexOf("pheedo.com")>=0
          || imgUrl.indexOf("hits.guardian")>=0
          || imgUrl.indexOf("invitemedia.com")>=0
          || imgUrl.indexOf("feeds.gawker.com")>=0
          || imgUrl.indexOf("pixel.quantserve.com")>=0
          || imgUrl.indexOf("feeds.gawker.com")>=0
          || imgUrl.indexOf("feeds.macnn.com")>=0
            )
          {
            //debug("addblock FIXED   "+adElement.src);
            //adElement.src = "http://127.0.0.1";
            var parent = adElement.parentNode;
            parent.removeChild(adElement);
            //if(info=="Gizmodo")
            //  debug("adblock remove parent of suspicios img src=["+imgUrl+"]"); 
            continue;
          }
          else
          {
            //if(info=="Gizmodo")
            //  debug("adblock NOT remove parent of img src=["+imgUrl+"]"); 
          }
        }

        var linkUrl;
        try {
          linkUrl = adElement.href;
        } catch(error) {}
        if(linkUrl)
        {
          if(linkUrl.indexOf("localhost/")>=0 || linkUrl.indexOf(".localhost")>=0
//        || linkUrl.indexOf("feedsportal")>=0          // telepolis
          || linkUrl.indexOf("feedburner.com")>=0
          || linkUrl.indexOf("doubleclick")>=0
          || linkUrl.indexOf("feedads")>=0
          || linkUrl.indexOf("pheedo.com")>=0
          || linkUrl.indexOf("hits.guardian")>=0
          || linkUrl.indexOf("invitemedia.com")>=0
          || linkUrl.indexOf("pixel.quantserve.com")>=0
            )
          {
            //debug("addblock FIXED   "+adElement.href);
            //adElement.href = "http://127.0.0.1";
            var parent = adElement.parentNode;
            parent.removeChild(adElement);
            //if(info=="Gizmodo")
            //  debug("adblock remove parent of suspicios link=["+linkUrl+"]"); 
            continue;
          }
          else
          {
            //if(info=="telepolis")
            //  debug("adblock NOT remove parent of link=["+linkUrl+"]"); 
          }
        }
      }
    }
  }

  var allBrElements = element.getElementsByTagName('br');
  if(allBrElements)
  {
    var elementCount = allBrElements.length;
    for(var msgNr=0; msgNr<elementCount; msgNr++)
    {
      var brElement = allBrElements[msgNr];
      if(brElement)
      {
        var parent = brElement.parentNode;
        parent.removeChild(brElement);
      }
    }
  }
}


