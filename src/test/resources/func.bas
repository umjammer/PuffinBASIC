10 PRINT ABS(-2), ABS(2), ABS(0)
20 PRINT ASC("A"), ASC("B")
30 PRINT sin(1), cos(1), tan(1), atn(1)
40 PRINT SQR(9), "log=", LOG(0.1)
50 PRINT CINT(2.3), CLNG(2.3), CSNG(2.3), CDBL(2.3)
60 PRINT INT(2.3), INT(-2.3)
70 PRINT FIX(2.3), FIX(-2.3)
80 PRINT SGN(10), SGN(0), SGN(-10)
90 PRINT CVI(MKI$(23)), CVL(MKL$(23)), CVS(MKS$(23.1)), CVD(MKD$(23.1))
100 PRINT STR$(102), SPACE$(4), STR$(102)
110 PRINT LEN("123")
120 PRINT HEX$(16), OCT$(8)
130 PRINT LEFT$("ABCD", 2), LEFT$("1234", 4)
140 PRINT RIGHT$("ABCD", 2), RIGHT$("1234", 4)
150 PRINT MID$("123456", 2, 2)
160 PRINT STRING$(3, "#"), STRING$(3, "ABC")
170 PRINT INSTR("12FOO34FOO", "FOO")
180 PRINT RND > 0
190 PRINT TIMER > 0