import static tech.tablesaw.api.ColumnType.*
import tech.tablesaw.api.*

table = Table.create("valresultat").addColumns(
  STRING.create("Förk"),
  STRING.create("Parti"),
  INTEGER.create("Antal"),
  DOUBLE.create("Andel")
)
"""
S	Arbetarepartiet-Socialdemokraterna	1 303 451	30,3%
SD	Sverigedemokraterna	888 371	20,7%
M	Moderaterna	816 028	19,0%
V	Vänsterpartiet	290 670	6,8%
C	Centerpartiet	288 721	6,7%
KD	Kristdemokraterna	230 842	5,4%
MP	Miljöpartiet de gröna	216 943	5,0%
L	Liberalerna (tidigare Folkpartiet)	197 804	4,6%
ÖVR	Övriga anmälda partier	65 043	1,5%
""".trim().eachLine {
  cols = it.split('\t')
  row = table.appendRow()
  row.setString("Förk", cols[0])
  row.setString("Parti", cols[1])
  def antal = cols[2].replaceAll("[^0-9]", "")
  row.setInt("Antal", Integer.parseInt(antal))
  row.setDouble("Andel", Double.parseDouble(cols[3].replaceAll(',', '.').replaceAll("[^0-9\\.]", "")))
}
// TODO: add totals
// TODO: split into left and right block
table
