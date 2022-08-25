package examplepackage

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tech.tablesaw.api.*

/**
 * Represents a country with additional ISO code info
 */
enum Country {

  // int countryId, String name, String swedishName, String a2Code, String a3Code, String numCode

  ABW(533, "Aruba", "AW", "ABW", "533"),
  AFG(4, "Afghanistan", "AF", "AFG", "004"),
  AGO(24, "Angola", "AO", "AGO", "024"),
  AIA(660, "Anguilla", "AI", "AIA", "660"),
  ALA(248, "Åland Islands", "AX", "ALA", "248"),
  ALB(8, "Albania", "AL", "ALB", "008"),
  AND(20, "Andorra", "AD", "AND", "020"),
  // Historical record, ANT ceased to exist in 2010
  ANT(530, "Netherlands Antilles", "AN", "ANT", "530"),
  ARE(784, "United Arab Emirates", "AE", "ARE", "784"),
  ARG(32, "Argentina", "AR", "ARG", "032"),
  ARM(51, "Armenia", "AM", "ARM", "051"),
  ASM(16, "American Samoa", "AS", "ASM", "016"),
  ATA(10, "Antarctica", "AQ", "ATA", "010"),
  ATF(260, "French Southern Territories", "TF", "ATF", "260"),
  ATG(28, "Antigua and Barbuda", "AG", "ATG", "028"),
  AUS(36, "Australia", "AU", "AUS", "036"),
  AUT(40, "Austria", "AT", "AUT", "040"),
  AZE(31, "Azerbaijan", "AZ", "AZE", "031"),
  BDI(108, "Burundi", "BI", "BDI", "108"),
  BEL(56, "Belgium", "BE", "BEL", "056"),
  BEN(204, "Benin", "BJ", "BEN", "204"),
  BES(535, "Bonaire, Sint Eustatius and Saba", "BQ", "BES", "535"),
  BFA(854, "Burkina Faso", "BF", "BFA", "854"),
  BGD(50, "Bangladesh", "BD", "BGD", "050"),
  BGR(100, "Bulgaria", "BG", "BGR", "100"),
  BHR(48, "Bahrain", "BH", "BHR", "048"),
  BHS(44, "Bahamas", "BS", "BHS", "044"),
  BIH(70, "Bosnia and Herzegovina", "BA", "BIH", "070"),
  BLM(652, "Saint Barthélemy", "BL", "BLM", "652"),
  BLR(112, "Belarus", "BY", "BLR", "112"),
  BLZ(84, "Belize", "BZ", "BLZ", "084"),
  BMU(60, "Bermuda", "BM", "BMU", "060"),
  BOL(68, "Bolivia (Plurinational State of)", "BO", "BOL", "068"),
  BRA(76, "Brazil", "BR", "BRA", "076"),
  BRB(52, "Barbados", "BB", "BRB", "052"),
  BRN(96, "Brunei Darussalam", "BN", "BRN", "096"),
  BTN(64, "Bhutan", "BT", "BTN", "064"),
  BWA(72, "Botswana", "BW", "BWA", "072"),
  BVT(74, "Bouvet Island", "BV", "BVT", "074"),
  CAF(140, "Central African Republic", "CF", "CAF", "140"),
  CAN(124, "Canada", "CA", "CAN", "124"),
  CCK(166, "Cocos (Keeling), Islands", "CC", "CCK", "166"),
  CHE(756, "Switzerland", "CH", "CHE", "756"),
  CHL(152, "Chile", "CL", "CHL", "152"),
  CHN(156, "China", "CN", "CHN", "156"),
  CIV(384, "Côte d'Ivoire", "CI", "CIV", "384"),
  CMR(120, "Cameroon", "CM", "CMR", "120"),
  COD(180, "Congo (Democratic Republic of the)", "CD", "COD", "180"),
  COG(178, "Congo", "CG", "COG", "178"),
  COK(184, "Cook Islands", "CK", "COK", "184"),
  COL(170, "Colombia", "CO", "COL", "170"),
  COM(174, "Comoros", "KM", "COM", "174"),
  CPV(132, "Cabo Verde", "CV", "CPV", "132"),
  CRI(188, "Costa Rica", "CR", "CRI", "188"),
  CUB(192, "Cuba", "CU", "CUB", "192"),
  CUW(531, "Curaçao", "CW", "CUW", "531"),
  CXR(162, "Christmas Island", "CX", "CXR", "162"),
  CYM(136, "Cayman Islands", "KY", "CYM", "136"),
  CYP(196, "Cyprus", "CY", "CYP", "196"),
  CZE(203, "Czechia", "CZ", "CZE", "203"),
  DEU(276, "Germany", "DE", "DEU", "276"),
  DJI(262, "Djibouti", "DJ", "DJI", "262"),
  DMA(212, "Dominica", "DM", "DMA", "212"),
  DNK(208, "Denmark", "DK", "DNK", "208"),
  DOM(214, "Dominican Republic", "DO", "DOM", "214"),
  DZA(12, "Algeria", "DZ", "DZA", "012"),
  ECU(218, "Ecuador", "EC", "ECU", "218"),
  EGY(818, "Egypt", "EG", "EGY", "818"),
  ERI(232, "Eritrea", "ER", "ERI", "232"),
  ESH(732, "Western Sahara", "EH", "ESH", "732"),
  ESP(724, "Spain", "ES", "ESP", "724"),
  EST(233, "Estonia", "EE", "EST", "233"),
  ETH(231, "Ethiopia", "ET", "ETH", "231"),
  FIN(246, "Finland", "FI", "FIN", "246"),
  FJI(242, "Fiji", "FJ", "FJI", "242"),
  FLK(238, "Falkland Islands (Malvinas)", "FK", "FLK", "238"),
  FRA(250, "France", "FR", "FRA", "250"),
  FRO(234, "Faroe Islands", "FO", "FRO", "234"),
  FSM(583, "Micronesia (Federated States of)", "FM", "FSM", "583"),
  GAB(266, "Gabon", "GA", "GAB", "266"),
  GBR(826, "United Kingdom of Great Britain and Northern Ireland", "GB", "GBR", "826"),
  GEO(268, "Georgia", "GE", "GEO", "268"),
  GGY(831, "Guernsey", "GG", "GGY", "831"),
  GHA(288, "Ghana", "GH", "GHA", "288"),
  GIB(292, "Gibraltar", "GI", "GIB", "292"),
  GIN(324, "Guinea", "GN", "GIN", "324"),
  GLP(312, "Guadeloupe", "GP", "GLP", "312"),
  GMB(270, "Gambia", "GM", "GMB", "270"),
  GNB(624, "Guinea-Bissau", "GW", "GNB", "624"),
  GNQ(226, "Equatorial Guinea", "GQ", "GNQ", "226"),
  GRC(300, "Greece", "GR", "GRC", "300"),
  GRD(308, "Grenada", "GD", "GRD", "308"),
  GRL(304, "Greenland", "GL", "GRL", "304"),
  GTM(320, "Guatemala", "GT", "GTM", "320"),
  GUF(254, "French Guiana", "GF", "GUF", "254"),
  GUM(316, "Guam", "GU", "GUM", "316"),
  GUY(328, "Guyana", "GY", "GUY", "328"),
  HKG(344, "Hong Kong", "HK", "HKG", "344"),
  HMD(334, "Heard Island and McDonald Islands", "HM", "HMD", "334"),
  HND(340, "Honduras", "HN", "HND", "340"),
  HRV(191, "Croatia", "HR", "HRV", "191"),
  HTI(332, "Haiti", "HT", "HTI", "332"),
  HUN(348, "Hungary", "HU", "HUN", "348"),
  IDN(360, "Indonesia", "ID", "IDN", "360"),
  IMN(833, "Isle of Man", "IM", "IMN", "833"),
  IND(356, "India", "IN", "IND", "356"),
  IOT(86, "British Indian Ocean Territory", "IO", "IOT", "086"),
  IRL(372, "Ireland", "IE", "IRL", "372"),
  IRN(364, "Iran(Islamic Republic of),", "IR", "IRN", "364"),
  IRQ(368, "Iraq", "IQ", "IRQ", "368"),
  ISL(352, "Iceland", "IS", "ISL", "352"),
  ISR(376, "Israel", "IL", "ISR", "376"),
  ITA(380, "Italy", "IT", "ITA", "380"),
  JAM(388, "Jamaica", "JM", "JAM", "388"),
  JEY(832, "Jersey", "JE", "JEY", "832"),
  JOR(400, "Jordan", "JO", "JOR", "400"),
  JPN(392, "Japan", "JP", "JPN", "392"),
  KAZ(398, "Kazakhstan", "KZ", "KAZ", "398"),
  KEN(404, "Kenya", "KE", "KEN", "404"),
  KGZ(417, "Kyrgyzstan", "KG", "KGZ", "417"),
  KHM(116, "Cambodia", "KH", "KHM", "116"),
  KIR(296, "Kiribati", "KI", "KIR", "296"),
  KNA(659, "Saint Kitts and Nevis", "KN", "KNA", "659"),
  KOR(410, "Korea (Republic of)", "KR", "KOR", "410"),
  KWT(414, "Kuwait", "KW", "KWT", "414"),
  LAO(418, "Lao People's Democratic Republic", "LA", "LAO", "418"),
  LBN(422, "Lebanon", "LB", "LBN", "422"),
  LBR(430, "Liberia", "LR", "LBR", "430"),
  LBY(434, "Libya", "LY", "LBY", "434"),
  LCA(662, "Saint Lucia", "LC", "LCA", "662"),
  LIE(438, "Liechtenstein", "LI", "LIE", "438"),
  LKA(144, "Sri Lanka", "LK", "LKA", "144"),
  LSO(426, "Lesotho", "LS", "LSO", "426"),
  LTU(440, "Lithuania", "LT", "LTU", "440"),
  LUX(442, "Luxembourg", "LU", "LUX", "442"),
  LVA(428, "Latvia", "LV", "LVA", "428"),
  MAC(446, "Macao", "MO", "MAC", "446"),
  MAF(663, "Saint Martin(French part)", "MF", "MAF", "663"),
  MAR(504, "Morocco", "MA", "MAR", "504"),
  MCO(492, "Monaco", "MC", "MCO", "492"),
  MDA(498, "Moldova (Republic of)", "MD", "MDA", "498"),
  MDG(450, "Madagascar", "MG", "MDG", "450"),
  MDV(462, "Maldives", "MV", "MDV", "462"),
  MEX(484, "Mexico", "MX", "MEX", "484"),
  MHL(584, "Marshall Islands", "MH", "MHL", "584"),
  MKD(807, "Macedonia (the former Yugoslav Republic of)", "MK", "MKD", "807"),
  MLI(466, "Mali", "ML", "MLI", "466"),
  MLT(470, "Malta", "MT", "MLT", "470"),
  MMR(104, "Myanmar", "MM", "MMR", "104"),
  MNE(499, "Montenegro", "ME", "MNE", "499"),
  MNG(496, "Mongolia", "MN", "MNG", "496"),
  MNP(580, "Northern Mariana Islands", "MP", "MNP", "580"),
  MOZ(508, "Mozambique", "MZ", "MOZ", "508"),
  MRT(478, "Mauritania", "MR", "MRT", "478"),
  MSR(500, "Montserrat", "MS", "MSR", "500"),
  MTQ(474, "Martinique", "MQ", "MTQ", "474"),
  MUS(480, "Mauritius", "MU", "MUS", "480"),
  MWI(454, "Malawi", "MW", "MWI", "454"),
  MYS(458, "Malaysia", "MY", "MYS", "458"),
  MYT(175, "Mayotte", "YT", "MYT", "175"),
  NAM(516, "Namibia", "NA", "NAM", "516"),
  NCL(540, "New Caledonia", "NC", "NCL", "540"),
  NER(562, "Niger", "NE", "NER", "562"),
  NFK(574, "Norfolk Island", "NF", "NFK", "574"),
  NGA(566, "Nigeria", "NG", "NGA", "566"),
  NIC(558, "Nicaragua", "NI", "NIC", "558"),
  NIU(570, "Niue", "NU", "NIU", "570"),
  NLD(528, "Netherlands", "NL", "NLD", "528"),
  NOR(578, "Norway", "NO", "NOR", "578"),
  NPL(524, "Nepal", "NP", "NPL", "524"),
  NRU(520, "Nauru", "NR", "NRU", "520"),
  NZL(554, "New Zealand", "NZ", "NZL", "554"),
  OMN(512, "Oman", "OM", "OMN", "512"),
  PAK(586, "Pakistan", "PK", "PAK", "586"),
  PAN(591, "Panama", "PA", "PAN", "591"),
  PCN(612, "Pitcairn", "PN", "PCN", "612"),
  PER(604, "Peru", "PE", "PER", "604"),
  PHL(608, "Philippines", "PH", "PHL", "608"),
  PLW(585, "Palau", "PW", "PLW", "585"),
  PNG(598, "Papua New Guinea", "PG", "PNG", "598"),
  POL(616, "Poland", "PL", "POL", "616"),
  PRI(630, "Puerto Rico", "PR", "PRI", "630"),
  PRK(408, "Korea (Democratic People's Republic of)", "KP", "PRK", "408"),
  PRT(620, "Portugal", "PT", "PRT", "620"),
  PRY(600, "Paraguay", "PY", "PRY", "600"),
  PSE(275, "Palestine, State of", "PS", "PSE", "275"),
  PYF(258, "French Polynesia", "PF", "PYF", "258"),
  QAT(634, "Qatar", "QA", "QAT", "634"),
  REU(638, "Réunion", "RE", "REU", "638"),
  RKS(383, "Kosovo", "XK", "XKX", "383"),
  ROU(642, "Romania", "RO", "ROU", "642"),
  RUS(643, "Russian Federation", "RU", "RUS", "643"),
  RWA(646, "Rwanda", "RW", "RWA", "646"),
  SAU(682, "Saudi Arabia", "SA", "SAU", "682"),
  SDN(729, "Sudan", "SD", "SDN", "729"),
  SEN(686, "Senegal", "SN", "SEN", "686"),
  SGP(702, "Singapore", "SG", "SGP", "702"),
  SGS(239, "South Georgia and the South Sandwich Islands", "GS", "SGS", "239"),
  SHN(654, "Saint Helena, Ascension and Tristan da Cunha", "SH", "SHN", "654"),
  SJM(744, "Svalbard and Jan Mayen", "SJ", "SJM", "744"),
  SLB(90, "Solomon Islands", "SB", "SLB", "090"),
  SLE(694, "Sierra Leone", "SL", "SLE", "694"),
  SLV(222, "El Salvador", "SV", "SLV", "222"),
  SMR(674, "San Marino", "SM", "SMR", "674"),
  SOM(706, "Somalia", "SO", "SOM", "706"),
  SPM(666, "Saint Pierre and Miquelon", "PM", "SPM", "666"),
  SRB(688, "Serbia", "RS", "SRB", "688"),
  SSD(728, "South Sudan", "SS", "SSD", "728"),
  STP(678, "Sao Tome and Principe", "ST", "STP", "678"),
  SUR(740, "Suriname", "SR", "SUR", "740"),
  SWE(752, "Sweden", "SE", "SWE", "752"),
  SVK(703, "Slovakia", "SK", "SVK", "703"),
  SVN(705, "Slovenia", "SI", "SVN", "705"),
  SWZ(748, "Swaziland", "SZ", "SWZ", "748"),
  SXM(534, "Sint Maarten (Dutch part)", "SX", "SXM", "534"),
  SYC(690, "Seychelles", "SC", "SYC", "690"),
  SYR(760, "Syrian Arab Republic", "SY", "SYR", "760"),
  TCA(796, "Turks and Caicos Islands", "TC", "TCA", "796"),
  TCD(148, "Chad", "TD", "TCD", "148"),
  TGO(768, "Togo", "TG", "TGO", "768"),
  THA(764, "Thailand", "TH", "THA", "764"),
  TJK(762, "Tajikistan", "TJ", "TJK", "762"),
  TKL(772, "Tokelau", "TK", "TKL", "772"),
  TKM(795, "Turkmenistan", "TM", "TKM", "795"),
  TLS(626, "Timor-Leste", "TL", "TLS", "626"),
  TON(776, "Tonga", "TO", "TON", "776"),
  TTO(780, "Trinidad and Tobago", "TT", "TTO", "780"),
  TUN(788, "Tunisia", "TN", "TUN", "788"),
  TUR(792, "Turkey", "TR", "TUR", "792"),
  TUV(798, "Tuvalu", "TV", "TUV", "798"),
  TWN(158, "Taiwan, Province of China", "TW", "TWN", "158"),
  TZA(834, "Tanzania, United Republic of", "TZ", "TZA", "834"),
  UGA(800, "Uganda", "UG", "UGA", "800"),
  UKR(804, "Ukraine", "UA", "UKR", "804"),
  UMI(581, "United States Minor Outlying Islands", "UM", "UMI", "581"),
  URY(858, "Uruguay", "UY", "URY", "858"),
  USA(840, "United States of America", "US", "USA", "840"),
  UZB(860, "Uzbekistan", "UZ", "UZB", "860"),
  VAT(336, "Holy See", "VA", "VAT", "336"),
  VCT(670, "Saint Vincent and the Grenadines", "VC", "VCT", "670"),
  VEN(862, "Venezuela (Bolivarian Republic of)", "VE", "VEN", "862"),
  VGB(92, "Virgin Islands (British)", "VG", "VGB", "092"),
  VIR(850, "Virgin Islands (U.S.)", "VI", "VIR", "850"),
  WLF(876, "Wallis and Futuna", "WF", "WLF", "876"),
  VNM(704, "Viet Nam", "VN", "VNM", "704"),
  WSM(882, "Samoa", "WS", "WSM", "882"),
  VUT(548, "Vanuatu", "VU", "VUT", "548"),
  YEM(887, "Yemen", "YE", "YEM", "887"),
  ZAF(710, "South Africa", "ZA", "ZAF", "710"),
  ZMB(894, "Zambia", "ZM", "ZMB", "894"),
  ZWE(716, "Zimbabwe", "ZW", "ZWE", "716")

  private static Logger logger = LoggerFactory.getLogger(Country.class)

  private static final int CALL_STACK_DEPTH = 2
  private static Table table
  
  static {
    table = Table.create("Countries",
      IntColumn.create("countryId"),
      StringColumn.create("name"),
      StringColumn.create("a2Code"),
      StringColumn.create("a3Code"),
      StringColumn.create("numCode")
    )
    for (c in COUNTRIES) {
      def row = table.appendRow()
      row.setInt(0, c.countryId)
      row.setString(1, c.name)
      row.setString(2, c.a2Code)
      row.setString(3, c.a3Code)
      row.setString(4, c.numCode)
    }
  }
  
  private final int countryId
  private final String name
  private final String a2Code
  private final String a3Code
  private final String numCode

  Country(int countryId, String name, String a2Code, String a3Code, String numCode) {
    this.countryId = countryId
    this.name = name
    this.a2Code = a2Code
    this.a3Code = a3Code
    this.numCode = numCode
  }

  int getCountryId() {
    return countryId
  }

  String getName() {
    return name
  }

  String getA2Code() {
    return a2Code
  }

  String getA3Code() {
    return a3Code
  }

  String getNumCode() {
    return numCode
  }

  static final List<Country> COUNTRIES = List.of(values())

  /**
   * @param isoNumCode the iso 3166 numeric code, eg 752
   * @return the corresponding Country based on iso 3166 numeric code, eg 752
   */
  static Country countryForNumCode(String isoNumCode) {
    if (isoNumCode == null) {
      return null
    }
    String numCode = isoNumCode
    if (numCode.length() == 2) {
      numCode = "0" + numCode
    } else if (numCode.length() == 1) {
      numCode = "00" + numCode
    }
    final String code = numCode
    Country country = COUNTRIES.stream().filter(p -> p.numCode == code).findAny().orElse(null)
    if (country == null) {
      logger.warn("Unknown ISO Number code for country: " + numCode)
      if (logger.isDebugEnabled()) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace()
        if (stackTraceElements.length > CALL_STACK_DEPTH) {
          StackTraceElement caller = stackTraceElements[CALL_STACK_DEPTH]
          logger.debug("countryForNumCode called from {}.{} line {}",
              caller.getClassName(), caller.getMethodName(), caller.getLineNumber())
        }
      }
    }
    return country
  }

  /**
   * @param a2Code the iso 3166 alpha 2 code (e.g. SE)
   * @return the corresponding Country based on iso 3166 alpha 2 code (e.g. SE)
   */
  static Country countryForA2Code(String a2Code) {
    Country country =  COUNTRIES.stream().filter(p -> p.a2Code.equals(a2Code)).findAny().orElse(null)
    if (country == null) {
      logger.warn("Unknown ISO a2 code for country: " + a2Code)
      if (logger.isDebugEnabled()) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace()
        if (stackTraceElements.length > CALL_STACK_DEPTH) {
          StackTraceElement caller = stackTraceElements[CALL_STACK_DEPTH]
          logger.debug("countryForA2Code called from {}.{} line {}",
              caller.getClassName(), caller.getMethodName(), caller.getLineNumber())
        }
      }
    }
    return country
  }


  /**
   * @param a3Code the iso 3166 alpha 2 code (e.g. SE)
   * @return the corresponding Country based on iso 3166 alpha 3 code (e.g. SWE)
   */
  static Country countryForA3Code(String a3Code) {
    Country country =  COUNTRIES.stream().filter(p -> p.a3Code.equals(a3Code)).findAny().orElse(null)
    if (country == null) {
      logger.warn("Unknown ISO a3 code for country: " + a3Code)
      if (logger.isDebugEnabled()) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace()
        if (stackTraceElements.length > CALL_STACK_DEPTH) {
          StackTraceElement caller = stackTraceElements[CALL_STACK_DEPTH]
          logger.debug("countryForA2Code called from {}.{} line {}",
              caller.getClassName(), caller.getMethodName(), caller.getLineNumber())
        }
      }
    }
    return country
  }
  
  
  static Table asTable() {
    return table
  }

}


