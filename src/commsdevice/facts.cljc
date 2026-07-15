(ns commsdevice.facts
  "Per-jurisdiction RADIO EQUIPMENT type-approval evidence catalog -- the
  G2-style spec-basis table the Radio-Compliance Governor checks every
  `:radio-compliance-rules/verify` proposal against.

  Distinct from `cloud-itonami-isic-2620`'s (computers/peripheral
  equipment) general EMC/product-safety self-declaration catalog
  (`deviceassembly.facts`): THIS device is a communication-equipment
  handset with an actual cellular/Wi-Fi/Bluetooth RADIO TRANSMITTER, so
  it needs RADIO EQUIPMENT type-approval on top of the general
  IEC 62368-1 safety baseline, not just general EMC:

    - Japan: 技術基準適合証明 (\"Giteki\" mark), administered by MIC
      (総務省, Ministry of Internal Affairs and Communications) via a
      登録証明機関 (registered certification body, e.g. TELEC) under the
      電波法 (Radio Act) -- distinct from isic-2620's VCCI/PSE (general
      EMC/electrical-safety) regime.
    - United States: FCC Part 15 Subpart C (intentional radiators,
      NOT isic-2620's Subpart B unintentional-radiator EMC-only regime)
      + FCC Part 22/24/27 (licensed cellular/PCS/wireless-communications
      services) equipment authorization (Certification, not SDoC
      self-declaration -- intentional radiators require FCC grant of an
      FCC ID, a materially stricter regime than Part 15B SDoC).
    - EU/Germany: Radio Equipment Directive 2014/53/EU (RED), which for
      radio transmitters routes through Article 3.2 essential
      requirements (efficient spectrum use, avoidance of harmful
      interference) and commonly a Notified Body opinion -- distinct
      from isic-2620's EMC Directive 2014/30/EU + RoHS (no radio
      transmitter, no RED applicability).
    - United Kingdom: Radio Equipment Regulations 2017 (SI 2017/1206,
      the UK's transposition of RED), UKCA marking via manufacturer
      self-declaration or an Approved Body -- distinct from isic-2620's
      general EMC Regulations 2016.

  Every jurisdiction below ALSO requires an RF-exposure / Specific
  Absorption Rate (SAR) test report -- a real, radio-transmitter-
  specific human-exposure evidence item with NO analog in isic-2620's
  catalog (a device with no intentional radiator has no SAR
  requirement) -- plus the general IEC 62368-1 safety baseline, which
  remains relevant here exactly as it does for isic-2620.

  Coverage is reported HONESTLY: a jurisdiction not in this table has
  NO spec-basis. Seed values cite official radio-type-approval
  authorities; this is a starting catalog, not a survey of every
  market.")

(def catalog
  {"JPN" {:name "Japan"
          :owner-authority "総務省 (MIC, Ministry of Internal Affairs and Communications) / 一般財団法人テレコムエンジニアリングセンター (TELEC, 登録証明機関)"
          :legal-basis "電波法 (Radio Act) 第38条の2 技術基準適合証明等 / 電波法施行規則 (参考)"
          :national-spec "特定無線設備の技術基準適合証明 (技適マーク, Giteki mark)"
          :provenance "https://www.tele.soumu.go.jp/"
          :required-evidence ["技術基準適合証明書 (giteki-type-approval-certificate)"
                              "無線設備規則試験報告書 (rf-conformance-test-report)"
                              "比吸収率(SAR)試験報告書 (sar-test-report)"
                              "IEC-62368-1安全性試験報告書 (IEC-62368-1-safety-test-report)"]}
   "USA" {:name "United States"
          :owner-authority "FCC (Federal Communications Commission), Office of Engineering and Technology"
          :legal-basis "47 CFR Part 15, Subpart C (Intentional Radiators) + 47 CFR Part 22/24/27 (licensed cellular/PCS/wireless-communications services) -- FCC Certification / grant of FCC ID (reference)"
          :national-spec "US FCC Certification (Equipment Authorization) for intentional-radiator cellular/Wi-Fi/Bluetooth transceivers"
          :provenance "https://www.fcc.gov/oet/ea/fccid"
          :required-evidence ["FCC-Part-15C-RF-test-report"
                              "FCC-Part-22-24-27-cellular-certification-report"
                              "RF-exposure-SAR-test-report"
                              "IEC-62368-1-safety-test-report"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "OPSS (Office for Product Safety and Standards) / Ofcom -- UKCA radio-equipment conformity"
          :legal-basis "Radio Equipment Regulations 2017 (SI 2017/1206, UK transposition of the EU Radio Equipment Directive) -- UKCA self-declaration or Approved Body route (reference)"
          :national-spec "UK UKCA-marking radio-equipment conformity via manufacturer Declaration of Conformity"
          :provenance "https://www.gov.uk/guidance/radio-equipment-regulations-2017"
          :required-evidence ["radio-equipment-RF-test-report"
                              "RF-exposure-SAR-test-report"
                              "IEC-62368-1-safety-test-report"]}
   "DEU" {:name "Germany (EU RED)"
          :owner-authority "Bundesnetzagentur (Funk-Marktüberwachung) / EU-Notified Body (Funkanlagen-Richtlinie Art. 3.2 Konformitätsbewertung)"
          :legal-basis "Funkanlagen-Richtlinie 2014/53/EU (Radio Equipment Directive, RED) Art. 3.2 (effiziente Frequenznutzung / Vermeidung schädlicher Störungen) (Referenz)"
          :national-spec "EU CE-Kennzeichnung für Funkanlagen unter RED, i.d.R. mit Notified-Body-Beteiligung für Art.-3.2-Anforderungen"
          :provenance "https://ec.europa.eu/growth/sectors/electrical-engineering/red-directive_en"
          :required-evidence ["RED-Funk-Prüfbericht (RED-RF-test-report)"
                              "SAR-Prüfbericht (RF-exposure-SAR-test-report)"
                              "IEC-62368-1-Sicherheitsprüfbericht (IEC-62368-1-safety-test-report)"]}})

(defn spec-basis [iso3] (get catalog iso3))

(defn coverage
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-2630 R0: " (count catalog)
                 " jurisdictions seeded. Extend `commsdevice.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
