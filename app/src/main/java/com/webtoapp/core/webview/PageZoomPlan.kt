package com.webtoapp.core.webview

import kotlin.math.roundToInt














internal data class PageZoomPlan(
    
    val zoomActive: Boolean,
    



    val initialScalePercent: Int
)

internal fun planPageZoom(pageZoomPercent: Int, initialScaleField: Int): PageZoomPlan {
    
    val zoom = if (pageZoomPercent > 0) pageZoomPercent else 100
    if (zoom != 100) return PageZoomPlan(zoomActive = true, initialScalePercent = zoom)
    
    return PageZoomPlan(zoomActive = false, initialScalePercent = if (initialScaleField > 0) initialScaleField else 0)
}









internal data class PageZoomViewport(
    val layoutWidthPx: Int,
    val scale: Double,
)

internal fun planPageZoomViewport(baseCssWidth: Int, pageZoomPercent: Int): PageZoomViewport? {
    val plan = planPageZoom(pageZoomPercent, 0)
    if (!plan.zoomActive || baseCssWidth <= 0) return null
    val zoom = plan.initialScalePercent / 100.0
    val layout = (baseCssWidth / zoom).roundToInt().coerceAtLeast(1)
    return PageZoomViewport(layoutWidthPx = layout, scale = baseCssWidth.toDouble() / layout)
}















@Suppress("UNUSED_PARAMETER")
internal fun pageZoomDocumentScript(
    pageZoomPercent: Int,
    pinchEnabled: Boolean,
    nativeCssWidth: Int = 0,
): String? {
    val plan = planPageZoom(pageZoomPercent, 0)
    if (!plan.zoomActive) return null
    return PAGE_ZOOM_JS.replace("PCT_PLACEHOLDER", plan.initialScalePercent.toString())
}

private const val PAGE_ZOOM_JS = """(function(){
'use strict';
try{if(window.top!==window)return;}catch(e){return;}
var PCT=PCT_PLACEHOLDER;
var Z=PCT/100;
var value=String(Z);
function root(){return document.documentElement;}
function apply(){
var el=root();
if(!el)return;
if(el.style.zoom===value)return;
el.style.zoom=value;
}
if(window.__wtaPageZoomPercent===PCT){
apply();
return;
}
window.__wtaPageZoomPercent=PCT;
apply();
if(document.readyState==='loading'){
document.addEventListener('DOMContentLoaded',function(){apply();});
}
if(typeof MutationObserver==='function'&&root()&&!window.__wtaPageZoomMo){
var writing=false;
window.__wtaPageZoomMo=new MutationObserver(function(){
if(writing)return;
var el=root();
if(!el||el.style.zoom===value)return;
writing=true;
el.style.zoom=value;
writing=false;
});
window.__wtaPageZoomMo.observe(root(),{attributes:true,attributeFilter:['style']});
}
})();"""
