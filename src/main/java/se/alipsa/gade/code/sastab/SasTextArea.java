package se.alipsa.gade.code.sastab;

import javafx.scene.control.ContextMenu;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.code.CodeComponent;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.utils.DefaultTaskListener;

import java.util.Collection;
import java.util.Collections;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SasTextArea extends CodeTextArea {

  // Since T and F are not true keywords (they can be reassigned e.g. T <- FALSE), they are not included below
  private static final String[] KEYWORDS = new String[]{

      "_all_",

      "abort","access", "aceclus", "allele", "anom", "anova", "append", "arima", "array", "attrib", "autoreg", "axis",

      "bom", "boxplot", "btl",

      "calendar", "calis", "callrfc", "cancorr", "candisc", "capability", "casecontrol", "catalog", "catmod", "chart",
      "cimport", "clear", "clp", "cluster", "compare", "computab", "contents", "convert", "copy", "corr", "corresp",
      "countreg", "cpm", "cport", "cusum",

      "datasets", "datasource", "dbf", "dbload", "define_event", "define_tagset", "dif", "discrim", "display",
      "distance", "document", "dqmatch", "dqscheme", "dqsrvadm", "dqsrvsvc", "dtree",

      "endrsubmit", "entropy", "esm", "excel", "expand", "explode", "export",


      "factex", "factor", "family", "fastclus", "fcmp", "file", "filename", "fmm", "fontreg", "forecast", "format",
      "forms", "footnote", "format", "freq", "fsbrowse", "fsedit", "fsletter", "fslist", "fsview",

      "g3d", "g3grid", "ga", "gam", "ganno", "gantt", "gareabar", "gbarline", "gchart", "gcontour", "gdevice",
      "geneselect", "genmod", "geocode", "gfont", "gimport", "ginside", "gkeymap", "gkpi", "glimmix", "glm", "glmmod",
      "glmpower", "glmselect", "gmap", "goptions", "gplot", "gproject", "gradar", "graphics", "greduce", "gremove",
      "greplay", "groovy", "gslide", "gtestit", "gtile",

      "haplotype", "hpcountreg", "hpdmdb", "hpds2", "hpf", "hpfarimaspec", "hpfdiagnose", "hpfengine", "hpfesmspec",
      "hpfevents", "hpfexmspec", "hpfidmspec", "hpforest", "hpfselect", "hpfucmspec", "hplmixed", "hplogistic",
      "hpmixed", "hpneural", "hpnlin", "hpreduce", "hpreg", "hpsample", "hpseverity", "hpsummary", "html", "html5",
      "htsnp", "http",

      "if", "iml", "import", "inbreed", "infomaps", "intpoint", "ishikawa",

      "kde", "keep", "krige2d",

      "lattice", "lifereg", "lifetest", "listing", "loan", "loess", "logistic", "lp",

      "macontrol", "macro", "mapimport", "mcmc", "mdc", "mds", "means", "metadata", "mi", "mianalyze", "migrate",
      "mixed", "modeclus", "model", "multtest",

      "nested", "netdraw", "netflow", "nlin", "nlmixed", "nlp", "npar1way", "nput",

      "ods", "olap", "operate", "optex", "options", "optload", "optlp", "optmilp", "optmodel", "optqp", "optsave",
      "orthoreg",

      "panel", "pareto", "pdlreg", "phreg", "plan", "plm", "plot", "pls", "pm", "pmenu", "power", "powerpoint",
      "princomp", "prinqual", "print", "printto", "probit", "proc", "proto", "prtdef", "prtexp", "psmooth", "pwencode",

      "qdevice", "qlim", "quantreg",

      "rank", "reg", "registry", "reliability", "report", "robustreg", "rsreg", "rtf", "run",

      "scaproc", "score", "seqdesign", "seqtest", "server", "severity", "sgdesign", "sgpanel", "sgplot", "sgrender",
      "sgscatter", "shewhart", "sim2d", "similarity", "simlin", "simnormal", "soap", "sort", "spectra", "sql",
      "standard", "statespace", "statgraph", "stdize", "stepdisc", "stp", "summary", "surveyfreq", "surveylogistic",
      "surveymeans", "surveyphreg", "surveyreg", "surveyselect", "syslin",


      "tabulate", "tagsets", "tcalis", "template", "then", "timeid", "timeplot", "timeseries", "tpspline", "trans",
      "transpose", "transreg", "trantab", "tree", "tscsreg", "tspl", "ttest",

      "ucm", "univariate",

      "var", "varclus", "varcomp", "variogram", "varmax",

      "x11", "x12",

      // TODO unsorted:
      "drop", "put", "rand", "merge", "table", "tables",
      "id", "by", "define", "title", "format", "filename", "goptions", "class", "cards", "cards4", "listing",
      "retain", "close", "unique", "set", "alter", "drop", "add", "compute", "endcomp",
      "edit", "axis", "symbol", "dm", "entry", "entryfootnote", "entrytitle", "continuouslegend", "discretelegend",
      "bandplot", "barchart", "barchartparm", "bihistogram3dparm", "blockplot", "boxplot", "boxplotparm",
      "contourplotparm", "densityplot", "dropline", "ellipse", "ellipseparm", "fringeplot", "histogram",
      "histogramparm", "lineparm", "loessplot", "modelband", "needleplot", "pbsplineplot", "referenceline",
      "regressionplot", "scatterplot", "scatterplotmatrix", "seriesplot", "stepplot", "surfaceplotparm", "vectorplot",
      "dynamic", "signon", "signoff", "rdisplay", "rget", "waitfor", "listtask", "killtask", "edit", "style", "array",
      "proc", "data", "informat", "weight", "model", "declare", "sysecho", "columns", "column", "freq", "nloptions",
      "break", "rbreak", "disconnect", "describe", "execute", "update", "reset", "insert", "validate", "ranks", "where",
      "replace", "value", "parmcards", "parmcards4", "distinct", "into", "from", "group", "scores", "as", "nowd",
      "order", "plots",

      "goptions", "infile", "informat", "killtask", "legend", "libname", "listtask", "note", "ods", "options", "pattern", "rdisplay", "rget", "rsubmit", "select", "signoff", "signon", "symbol", "sysecho", "systask", "title", "waitfor", "where", "class", "table", "model", "freq", "weight", "by", "xaxis", "yaxis", "xaxis2", "yaxis2",


      "$include", "_all_", "_character_", "_cmd_", "_error_", "_freq_", "_i_", "_infile_", "_last_", "_msg_", "_n_", "_null_", "_numeric_", "_temporary_", "_type_", "abort", "addr", "adjrsq", "alpha", "alter", "altlog", "altprint", "array", "attrib", "authserver", "autoexec", "awscontrol", "awsdef", "awsmenu", "awsmenumerge", "awstitle", "backward", "base", "blocksize", "bufno", "bufsize", "by", "byerr", "byline", "call", "cards", "cards4", "catcache", "cbufno", "center", "change", "chisq", "class", "cleanup", "cntllev", "codegen", "col", "collin", "column", "comamid", "comaux1", "comaux2", "comdef", "config", "continue", "cpuid", "create", "datalines", "datalines4", "dbcslang", "dbcstype", "ddm", "delete", "delimiter", "descending", "device", "dflang", "display", "distinct", "dkricond", "dkrocond", "dlm", "do", "drop", "dsnferr", "echo", "else", "emaildlg", "emailid", "emailpw", "emailserver", "emailsys", "encrypt", "end", "endsas", "engine", "eof", "eov", "error", "errorcheck", "errors", "feedback", "file", "fileclose", "filefmt", "filevar", "first", "first.", "firstobs", "fmterr", "fmtsearch", "font", "fontalias", "footnote", "footnote1", "footnote2", "footnote3", "footnote4", "footnote5", "footnote6", "footnote7", "footnote8", "footnote9", "force", "formatted", "formchar", "formdelim", "formdlim", "forward", "from", "go", "goto", "group", "gwindow", "hbar", "helpenv", "helploc", "honorappearance", "hostprint", "hpct", "html", "hvar", "ibr", "id", "if", "infile", "informat", "initcmd", "initstmt", "inr", "into", "invaliddata", "is", "join", "keep", "kentb", "label", "last", "last.", "leave", "lib", "library", "line", "linesize", "link", "list", "lostcard", "lrecl", "ls", "macro", "macrogen", "maps", "mautosource", "maxdec", "maxr", "measures", "median", "memtype", "merge", "merror", "missing", "missover", "mlogic", "mode", "model", "modify", "mprint", "mrecall", "msglevel", "msymtabmax", "mvarsize", "myy", "new", "news", "no", "nobatch", "nobs", "nocol", "nocaps", "nocardimage", "nocenter", "nocharcode", "nocmdmac", "nocum", "nodate", "nodbcs", "nodetails", "nodmr", "nodms", "nodmsbatch", "nodup", "nodupkey", "noduplicates", "noechoauto", "noequals", "noerrorabend", "noexitwindows", "nofullstimer", "noicon", "noimplmac", "noint", "nolist", "noloadlist", "nomiss", "nomlogic", "nomprint", "nomrecall", "nomsgcase", "nomstored", "nomultenvappl", "nonotes", "nonumber", "noobs", "noovp", "nopad", "noprint", "noprintinit", "norow", "norsasuser", "nosetinit", "nosource2", "nosplash", "nosymbolgen", "notes", "notitle", "notitles", "notsorted", "noverbose", "noxsync", "noxwait", "number", "numkeys", "nummousekeys", "nway", "obs", "ods", "option", "order", "otherwise", "out", "outp=", "output", "over", "ovp", "pad", "pad2", "page", "pageno", "pagesize", "paired", "parm", "parmcards", "path", "pathdll", "pfkey", "position", "printer", "probsig", "procleave", "prt", "ps", "pw", "pwreq", "quit", "r", "ranks", "read", "recfm", "reg", "register", "regr", "remote", "remove", "rename", "replace", "retain", "return", "reuse", "rsquare", "rtf", "rtrace", "rtraceloc", "s", "s2", "samploc", "sasautos", "sascontrol", "sasfrscr", "sashelp", "sasmsg", "sasmstore", "sasscript", "sasuser", "select", "selection", "separated", "seq", "serror", "set", "setcomm", "simple", "siteinfo", "skip", "sle", "sls", "sortedby", "sortpgm", "sortseq", "sortsize", "source2", "splashlocation", "split", "spool", "start", "stdin", "stimer", "stop", "stopover", "sumwgt", "symbol", "symbolgen", "sysin", "sysleave", "sysparm", "sysprint", "sysprintfont", "t", "table", "tables", "tapeclose", "tbufsize", "terminal", "test", "then", "title", "title1", "title2", "title3", "title4", "title5", "title6", "title7", "title8", "title9", "to", "tol", "tooldef", "trantab", "truncover", "type", "unformatted", "union", "until", "update", "user", "usericon", "validate", "value", "var", "varray", "varrayx", "vformat", "vformatd", "vformatdx", "vformatn", "vformatnx", "vformatw", "vformatwx", "vformatx", "vinarray", "vinarrayx", "vinformat", "vinformatd", "vinformatdx", "vinformatn", "vinformatnx", "vinformatw", "vinformatwx", "vinformatx", "vlabel", "vlabelx", "vlength", "vlengthx", "vname", "vnamex", "vnferr", "vtype", "vtypex", "weight", "when", "where", "while", "wincharset", "window", "work", "workinit", "workterm", "write", "x", "xsync", "xwait", "yearcutoff", "yes", "abs", "airy", "arcos", "arsin", "atan", "attrc", "attrn", "band", "betainv", "blshift", "bnot", "bor", "brshift", "bxor", "byte", "cdf", "ceil", "cexist", "cinv", "close", "cnonct", "collate", "compbl", "compound", "compress", "cos", "cosh", "css", "curobs", "cv", "daccdb", "daccdbsl", "daccsl", "daccsyd", "dacctab", "dairy", "date", "datejul", "datepart", "datetime", "day", "dclose", "depdb", "depdbsl", "depsl", "depsyd", "deptab", "dequote", "dhms", "dif", "digamma", "dim", "dinfo", "dnum", "dopen", "doptname", "doptnum", "dread", "dropnote", "dsname", "erf", "erfc", "exist", "exp", "fappend", "fclose", "fcol", "fdelete", "fetch", "fetchobs", "fexist", "fget", "fileexist", "filename", "fileref", "finfo", "finv", "fipname", "fipnamel", "fipstate", "floor", "fnonct", "fnote", "fopen", "foptname", "foptnum", "fpoint", "fpos", "fput", "fread", "frewind", "frlen", "fsep", "fuzz", "fwrite", "gaminv", "gamma", "getoption", "getvarc", "getvarn", "hbound", "hms", "hosthelp", "hour", "ibessel", "index", "indexc", "indexw", "input", "inputc", "inputn", "int", "intck", "intnx", "intrr", "irr", "jbessel", "juldate", "kurtosis", "lag", "lbound", "left", "length", "lgamma", "libname", "libref", "log", "log10", "log2", "logpdf", "logpmf", "logsdf", "lowcase", "max", "mdy", "mean", "min", "minute", "mod", "month", "mopen", "mort", "n", "netpv", "nmiss", "normal", "note", "npv", "open", "ordinal", "pathname", "pdf", "peek", "peekc", "pmf", "point", "poisson", "poke", "probbeta", "probbnml", "probchi", "probf", "probgam", "probhypr", "probit", "probnegb", "probnorm", "probt", "put", "putc", "putn", "qtr", "quote", "ranbin", "rancau", "ranexp", "rangam", "range", "rannor", "ranpoi", "rantbl", "rantri", "ranuni", "repeat", "resolve", "reverse", "rewind", "right", "round", "saving", "scan", "sdf", "second", "sign", "sin", "sinh", "skewness", "soundex", "spedis", "sqrt", "std", "stderr", "stfips", "stname", "stnamel", "substr", "sum", "symget", "symput", "sysget", "sysmsg", "sysprod", "sysrc", "system", "tan", "tanh", "time", "timepart", "tinv", "tnonct", "today", "translate", "tranwrd", "trigamma", "trim", "trimn", "trunc", "uniform", "upcase", "uss", "varfmt", "varinfmt", "varlabel", "varlen", "varname", "varnum", "vartype", "verify", "weekday", "year", "yyq", "zipfips", "zipname", "zipnamel", "zipstate", "crosstab", "descript", "design=", "levels", "nest", "setot", "subgroup", "subpopn", "totper", "wsum",


      "data=", "id=", "min=", "max=", "template=", "out=", "where=", "rename=",

      "armend", "armgtid", "arminit", "armjoin", "armproc", "armstop", "armstrt", "armupdt", "abs", "addr", "addrlong", "airy", "allperm", "anyalnum", "anyalpha", "anycntrl", "anydigit", "anyfirst", "anygraph", "anylower", "anyname", "anyprint", "anypunct", "anyspace", "anyupper", "anyxdigit", "arcos", "arcosh", "arsin", "arsinh", "artanh", "atan", "atan2", "attrc", "attrn", "band", "beta", "betainv", "blackclprc", "blackptprc", "blkshclprc", "blshift", "bnot", "bor", "brshift", "bxor", "byte", "allcomb", "allcombi", "cats", "catt", "catx", "compcost", "graycode", "label", "lexcomb", "lexcombi", "lexperk", "lexperm", "logistic", "missing", "module", "poke", "pokelong", "prxdebug", "prxfree", "prxnext", "prxsubstr", "ranbin", "rancau", "rannor", "ranperk", "ranperm", "ranpoi", "rantbl", "rantri", "ranuni", "set", "softmax", "sortc", "sortn", "stdize", "streaminit", "symput", "symputx", "vnext", "cat", "catq", "cdf", "ceil", "ceilz", "cexist", "char", "choosec", "choosen", "cinv", "close", "cmiss", "cnonct", "coalesce", "coalescec", "collate", "comb", "compare", "compbl", "compged", "complev", "compound", "compress", "constant", "convx", "convxp", "cos", "cosh", "count", "countc", "countw", "css", "curobs", "cv", "daccdb", "daccdbsl", "daccsl", "daccsyd", "dacctab", "dairy", "datdif", "date", "datejul", "datepart", "datetime", "day", "dclose", "dcreate", "depdb", "depdbsl", "depsl", "depsyd", "deptab", "dequote", "deviance", "dhms", "dif", "digamma", "dim", "dinfo", "divide", "dnum", "dopen", "doptname", "doptnum", "dread", "dropnote", "dsname", "dur", "durp", "envlen", "erf", "erfc", "euclid", "exist", "exp", "fact", "fappend", "fclose", "fcol", "fdelete", "fetch", "fetchobs", "fexist", "fget", "fileexist", "filename", "fileref", "finance", "find", "findc", "findw", "finfo", "finv", "fipname", "fipnamel", "fipstate", "first", "floor", "floorz", "fnonct", "fnote", "fopen", "foptname", "foptnum", "fpoint", "fpos", "fput", "fread", "frewind", "frlen", "fsep", "fuzz", "fwrite", "gaminv", "gamma", "garkhclprc", "garkhptprc", "gcd", "geodist", "geomean", "geomeanz", "getoption", "getvarc", "getvarn", "harmean", "harmeanz", "hbound", "hms", "holiday", "hour", "htmldecode", "htmlencode", "ibessel", "ifc", "ifn", "index", "indexc", "indexw", "input", "inputc", "inputn", "int", "intcindex", "intck", "intcycle", "intfit", "intfmt", "intget", "intindex", "intnx", "intrr", "intseas", "intshift", "inttest", "intz", "iqr", "iorcmsg", "irr", "jbessel", "juldate", "juldate7", "kurtosis", "lag", "largest", "lbound", "lcm", "lcomb", "left", "qleft", "length", "lengthc", "lengthm", "lengthn", "lfact", "lgamma", "libname", "libref", "log", "log1px", "log10", "log2", "logbeta", "logcdf", "logpdf", "logsdf", "lowcase", "qlowcase", "lperm", "lpnorm", "mad", "margrclprc", "margrptprc", "max", "md5", "mdy", "mean", "median", "min", "minute", "missing", "mod", "modulec", "modulen", "modz", "month", "mopen", "mort", "msplint", "n", "netpv", "nliteral", "nmiss", "notalnum", "notalpha", "notcntrl", "notdigit", "note", "notfirst", "notgraph", "notlower", "notname", "notprint", "notpunct", "notspace", "notupper", "notxdigit", "npv", "nvalid", "nwkdom", "open", "ordinal", "pathname", "pctl", "pdf", "peek", "peekc", "peekclong", "peeklong", "perm", "point", "poisson", "probbeta", "probbnml", "probbnrm", "probchi", "probf", "probgam", "probhypr", "probit", "probmc", "probnegb", "probnorm", "probt", "propcase", "prxchange", "prxmatch", "prxparen", "prxparse", "prxposn", "ptrlongadd", "put", "putc", "putn", "pvp", "qtr", "quantile", "quote", "rand", "ranexp", "rangam", "range", "rank", "rename", "repeat", "resolve", "reverse", "rewind", "right", "rms", "round", "rounde", "roundz", "saving", "scan", "scanq", "sdf", "second", "sign", "sin", "sinh", "skewness", "sleep", "smallest", "soundex", "spedis", "sqrt", "std", "stderr", "stfips", "stname", "stnamel", "strip", "subpad", "substr", "qsubstr", "substrn", "sum", "sumabs", "symexist", "symget", "symglobl", "symlocal", "sysmsg", "sysparm", "sysprocessid", "sysprocessname", "sysprod", "sysrc", "system", "tan", "tanh", "time", "timepart", "tinv", "tnonct", "today", "translate", "transtrn", "tranwrd", "trigamma", "trim", "qtrim", "trimn", "trunc", "uniform", "upcase", "qupcase", "urldecode", "urlencode", "uss", "uuidgen", "var", "varfmt", "varinfmt", "varlabel", "varlen", "varname", "varnum", "varray", "varrayx", "vartype", "verify", "vformat", "vformatd", "vformatdx", "vformatn", "vformatnx", "vformatw", "vformatwx", "vformatx", "vinarray", "vinarrayx", "vinformat", "vinformatd", "vinformatdx", "vinformatn", "vinformatnx", "vinformatw", "vinformatwx", "vinformatx", "vlabel", "vlabelx", "vlength", "vlengthx", "vname", "vnamex", "vtype", "vtypex", "vvalue", "vvaluex", "week", "weekday", "whichc", "whichn", "year", "yieldp", "yrdif", "yyq", "zipcity", "zipcitydistance", "zipfips", "zipname", "zipnamel", "zipstate", "ascebc", "delete", "ebcasc", "fileattr", "findfile", "getdvi", "getjpi", "getlog", "getmsg", "getquota", "getsym", "getterm", "nodename", "putlog", "putsym", "setterm", "termin", "termout", "ttclose", "ttcontrl", "ttopen", "ttread", "ttwrite", "vms", "bquote", "nrbquote", "eval", "nrquote", "str", "nrstr", "qscan", "superq", "sysevalf", "sysfunc", "qsysfunc", "sysget", "unquote", "cmpres", "qcmpres", "compstor", "datatyp", "dqcase", "dqgender", "dqgenderinfoget", "dqgenderparsed", "dqidentify", "dqlocaleguess", "dqlocaleinfoget", "dqlocaleinfolist", "dqmatch", "dqmatchinfoget", "dqmatchparsed", "dqparse", "dqparseinfoget", "dqparsetokenget", "dqparsetokenput", "dqpattern", "dqschemeapply", "dqsrvarchjob", "dqsrvcopylog", "dqsrvdeletelog", "dqsrvjobstatus", "dqsrvkilljob", "dqsrvprofjobfile", "dqsrvprofjobrep", "dqsrvuser", "dqstandardize", "dqtoken", "effrate", "mvalid", "nomrate", "savings", "soapweb", "soapwebmeta", "soapwipservice", "soapwipsrs", "soapws", "soapwsmeta", "squantile", "sysexist", "timevalue", "invcdf", "isnull", "limmoment", "read_array", "run_macro", "run_sasfile", "solve", "write_array", "grdsvc_enable", "grdsvc_getaddr", "grdsvc_getinfo", "grdsvc_getname", "grdsvc_nnodes"
  };

  // See https://www.rdocumentation.org/packages/base/versions/3.5.2 for more, at ns-dblcolon
  // or https://stat.ethz.ch/R-manual/R-devel/library/base/html/00Index.html
  // Will be too long for styling the textarea but useful for suggestions using ctrl + tab
  // see https://github.com/FXMisc/RichTextFX/issues/91 for some ideas
  private static final String[] FUNCTIONS = new String[]{
      "%bquote", "%do", "%else", "%end", "%eval", "%global", "%goto", "%if", "%inc", "%include", "%index", "%input",
      "%length", "%let", "%list", "%local", "%macro", "%mend", "%nrbquote", "%nrquote", "%nrstr", "%put", "%qscan",
      "%qsysfunc", "%quote", "%run", "%substr", "%syscall", "%sysevalf", "%sysexec", "%sysfunc", "%sysrc"," %then",
      "%to", "%until", "%while", "%window", "$1", "$2", "$3", "$4", "$5", "$6", "$7", "$8", "$9", "$ascii", "$binary",
      "$cb", "$char", "$charzb", "$ebcdic", "$hex", "$kanji", "$kanjix", "$msgcase", "$octal", "$phex", "$quote",
      "$reverj", "$revers", "$upcase", "$varying", "best", "binary", "bits", "bz", "cb", "char", "comma", "commax",
      "dateampm", "ddmmyy", "dollar", "dollarx", "downame", "eurdfdd", "eurdfde", "eurdfdn", "eurdfdt", "eurdfdwn",
      "eurdfmn", "eurdfmy", "eurdfwdx", "eurdfwkx", "float", "fract", "hex", "hhmm", "ib", "ieee", "julday", "julian",
      "minguo", "mmddyy", "mmss", "mmyy", "monname", "monyy", "msec", "negparen", "nengo", "numx", "octal", "pd",
      "pdjulg", "pdjuli", "pdtime", "percent", "pib", "pk", "punch", "pvalue", "qtrr", "rb", "rmfdur", "rmfstamp",
      "roman", "row", "s370ff", "s370fib", "s370fibu", "s370fpd", "s370fpdu", "s370fpib", "s370frb", "s370fzd",
      "s370fzdl", "s370fzds", "s370fzdt", "s370fzdu", "smfstamp", "timeampm", "tod", "tu", "vaxrb", "weekdate",
      "weekdatx", "worddate", "worddatx", "wordf", "words", "yen", "yymm", "yymmdd", "yymon", "yyqr", "z", "zd", "zdb",
      "zdv"
  };

  private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
  private static final String FUNCTIONS_PATTERN = "\\b(" + String.join("|", FUNCTIONS) + ")\\b";
  private static final String ASSIGNMENT_PATTERN = "->|<-|->>|<<-|=(?!=)|~|%>%|\\$";
  private static final String OPERATOR_PATTERN = "-|\\+|\\*|/|\\^|\\*{2}|%%|%/%|%in%|<|>|<=|>=|={2}|!=|!|&|:";
  private static final String BRACKET_PATTERN = "[\\[\\]\\{\\}\\(\\)]";
  private static final String DIGIT_PATTERN = "\\b\\d+";
  //private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"|\'([^\'\\\\]|\\\\.)*\'"; // backtracing makes this crazy slow
  private static final String STRING_PATTERN = "\"\"|''|\"[^\"]+\"|'[^']+'";
  private static final String COMMENT_PATTERN = "\\*[^\n]*;" + "|" + "(?:/\\*(?:[^*]|(?:\\*+[^*/]))*\\*+/)|(?://.*)";
  private static final Pattern PATTERN = Pattern.compile(
      "(?<COMMENT>" + COMMENT_PATTERN + ")"
          + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
          + "|(?<FUNCTIONS>" + FUNCTIONS_PATTERN + ")"
          + "|(?<ASSIGNMENT>" + ASSIGNMENT_PATTERN + ")"
          + "|(?<OPERATOR>" + OPERATOR_PATTERN + ")"
          + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
          + "|(?<DIGIT>" + DIGIT_PATTERN + ")"
          + "|(?<STRING>" + STRING_PATTERN + ")"
  );
  private static final Pattern LIGHT_PATTERN = Pattern.compile(
      "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
          + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
  );
  private static final Logger LOG = LogManager.getLogger(SasTextArea.class);
  ContextMenu suggestionsPopup = new ContextMenu();

  public SasTextArea() {
  }

  public SasTextArea(TextAreaTab parent) {
    super(parent);

    Gade gui = parent.getGui();
    ConsoleComponent console = gui.getConsoleComponent();
    addEventHandler(KeyEvent.KEY_PRESSED, e -> {
      if (e.isControlDown()) {
        if (KeyCode.ENTER.equals(e.getCode())) {
          CodeComponent codeComponent = gui.getCodeComponent();
          String rCode = getText(getCurrentParagraph()); // current line

          String selected = selectedTextProperty().getValue();
          // if text is selected then go with that instead
          if (selected != null && !"".equals(selected)) {
            rCode = codeComponent.getTextFromActiveTab();
          }
          if (parent instanceof TaskListener) {
            console.runScriptAsync(rCode, codeComponent.getActiveScriptName(), (TaskListener)parent);
          } else {
            console.runScriptAsync(rCode, codeComponent.getActiveScriptName(), new DefaultTaskListener());
          }
          moveTo(getCurrentParagraph() + 1, 0);
          int totalLength = getAllTextContent().length();
          if (getCaretPosition() > totalLength) {
            moveTo(totalLength);
          }
        } else if (KeyCode.SPACE.equals(e.getCode())) {
          autoComplete();
        }
      }
    });

  }

  protected final StyleSpans<Collection<String>> computeHighlighting(String text) {
    return computeFullHighlighting(text);
    /* // rewrote regexp for String pattern so do not need this now
    if (text.length() < 60000) {
      return computeFullHighlighting(text);
    } else {
      log.warn("Text is too large for full syntax coloring, using bare essentials");
      return computeLightHighlighting(text);
    }*/
  }

  protected final StyleSpans<Collection<String>> computeLightHighlighting(String text) {
    Matcher matcher = LIGHT_PATTERN.matcher(text);
    int lastKwEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass =
          matcher.group("KEYWORD") != null ? "keyword" :
              matcher.group("COMMENT") != null ? "comment" :
                  null; /* never happens */
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
      spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastKwEnd = matcher.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
    return spansBuilder.create();
  }

  protected final StyleSpans<Collection<String>> computeFullHighlighting(String text) {
    Matcher matcher = PATTERN.matcher(text);
    int lastKwEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass =
          matcher.group("KEYWORD") != null ? "keyword" :
              matcher.group("FUNCTIONS") != null ? "function" :
                matcher.group("ASSIGNMENT") != null ? "assign" :
                    matcher.group("OPERATOR") != null ? "operator" :
                        matcher.group("BRACKET") != null ? "bracket" :
                            matcher.group("DIGIT") != null ? "digit" :
                                matcher.group("STRING") != null ? "string" :
                                    matcher.group("COMMENT") != null ? "comment" :
                                        null; /* never happens */
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
      spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastKwEnd = matcher.end();
    }

    spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
    return spansBuilder.create();
  }

  /**
   * TODO: maybe a regex would be more performant?
   * "^.*?(\\w+)\\W*$" is not sufficient as it handles dots as word boundary
   */
  @Override
  public void autoComplete() {
    String line = getText(getCurrentParagraph());
    String currentText = line.substring(0, getCaretColumn());
    //System.out.println("Current text is " + currentText);
    String lastWord;
    int index = currentText.indexOf(' ');
    if (index == -1 ) {
      lastWord = currentText;
    } else {
      lastWord = currentText.substring(currentText.lastIndexOf(' ') + 1);
    }
    index = lastWord.indexOf('(');
    if (index > -1) {
      lastWord = lastWord.substring(index+1);
    }
    index = lastWord.indexOf('[');
    if (index > -1) {
      lastWord = lastWord.substring(index+1);
    }
    index = lastWord.indexOf('{');
    if (index > -1) {
      lastWord = lastWord.substring(index+1);
    }

    //System.out.println("Last word is '" + lastWord + "'");
    if (lastWord.length() > 0) {
      suggestCompletion(lastWord);
    }
  }

  private void suggestCompletion(String lastWord) {
    TreeMap<String, Boolean> suggestions = new TreeMap<>();

    for (String keyword : KEYWORDS) {
      if (keyword.startsWith(lastWord)) {
        suggestions.put(keyword, Boolean.FALSE);
      }
    }
    for (String function : FUNCTIONS) {
      if (function.startsWith(lastWord)) {
        suggestions.put(function + "()", Boolean.TRUE);
      }
    }
    suggestCompletion(lastWord, suggestions, suggestionsPopup);
  }

}
