var ctx = document.getElementById("image").getContext("2d");
var gitChart = null;

var lineOpts = {
	fillColor: "rgba(151,187,205,0.1)",
    strokeColor: "rgba(151,187,205,1)",
    pointColor: "rgba(151,187,205,1)",
    pointStrokeColor: "#fff",
    pointHighlightFill: "#fff",
    pointHighlightStroke: "rgba(151,187,205,1)",
};
function mergeOptions(obj1,obj2){
    var obj3 = {};
    for (var attrname in obj1) { obj3[attrname] = obj1[attrname]; }
    for (var attrname in obj2) { obj3[attrname] = obj2[attrname]; }
    return obj3;
}
function makeChart(repositories){
	var data = []; 
	repositories.forEach(function(el){
		var line = mergeOptions(lineOpts, {});
		line['label'] = el['name'];
		line['data'] = el['incrs'];
		data.push(line);
	});
	gitChart = new Chart(document.getElementById("image").getContext("2d")).Line(
		// data
		{
			labels: ["29.11.14", "30.11.14", "1.12.14", "2.12.14", "3.12.14", "4.12.14", "5.12.14"],
			datasets: data
		}, {
			datasetStroke : true,
			showTooltips: true,
		}
	);
	data.shift();
	gitChart = new Chart(document.getElementById("image1").getContext("2d")).Line(
		// data
		{
			labels: ["29.11.14", "30.11.14", "1.12.14", "2.12.14", "3.12.14", "4.12.14", "5.12.14"],
			datasets: data
		}, {
			datasetStroke : true,
			showTooltips: true,
		}
	);
	data.shift();
	var sss = "i wonna be..."
	var x = 1;
	gitChart = new Chart(document.getElementById("image2").getContext("2d")).Line(
		// data
		{
			labels: ["29.11.14", "30.11.14", "1.12.14", "2.12.14", "3.12.14", "4.12.14", "5.12.14"],
			datasets: data
		}, {
			datasetStroke : true,
			showTooltips: true,

			multiTooltipTemplate: "<%= name %><%= value %>",
		}
	);
}

// LOAD DATA

// Chart.defaults.global.tooltipTemplate = "<%=label%>:<%= value %>";

var xmlhttp = new XMLHttpRequest();
var url = "stat.json";

xmlhttp.onreadystatechange = function() {
    if (xmlhttp.readyState == 4 && xmlhttp.status == 200) {
        var myArr = JSON.parse(xmlhttp.responseText);
        makeChart(myArr);
    }
}

xmlhttp.open("GET", url, true);
xmlhttp.send();