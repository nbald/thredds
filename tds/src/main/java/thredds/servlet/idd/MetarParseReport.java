/*
 * MetarParseReport
 *
 * parses one METAR report into it's variables
 *
 */
package thredds.servlet.idd;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;

public class MetarParseReport {

    /**
     * Used to store fields values
    **/
    private LinkedHashMap<String, String> field = new LinkedHashMap();

    /**
     * Used to store fields units
    **/
    private HashMap<String, String> unit = new LinkedHashMap();


    static final Pattern WeatherPrecip = Pattern.compile("(\\+|-|VC|PR| )(MI|BC|DR|BL|SH|TS|FZ)?(DZ|RA|SN|SG|IC|PE|PL|GR|GS|UP)\\s+");

    static final Pattern WeatherObs = Pattern.compile("(\\+|-|VC|PR| )(MI|BC|DR|BL|SH|TS|FZ)?(BR|FG|FU|VA|DU|SA|HZ|PY)\\s+");

    static final Pattern WeatherOther = Pattern.compile("(\\+|-|VC|PR| )(MI|BC|DR|BL|SH|TS|FZ)?(PO|SQ|FC|SS|DS)\\s+");

    static final Pattern Temperature_tenths = Pattern.compile("T(0|1)(\\d{3})(0|1)?(\\d{3})?\\s+");

    static final Pattern Temperature = Pattern.compile("(M|-)?(\\d{2})/(M|-)?(\\d{2})?\\s*");


    public void parseReport(String input) {

        MetarPatterns p = new MetarPatterns();
        Matcher m;
        float var1, var2, var3;
        String report = null, remark = null;
        String [] split;

        //	return if( /^\n/ ) ;
        if (p.p_B_CR.matcher(input).find()) {
            field = null;
            return;
        }

        // 	split input into report and remark to distinguish fields
        split = p.p_REMARKS.split(input);
        report = split[ 0 ] +" ";
        if( split.length ==  2)
            remark = " "+ split[ 1 ] +" ";

        //	$rep_type = $1 if( s#^(METAR|SPECI|TESTM|TESTS) ## ) ;
        m = p.p_B_metar.matcher(report);
        if (m.find()) {
            field.put("Report_Type", m.group(1));
            //unit.put("Report_Type", "" );
            report = m.replaceFirst("");
        } else { // default
            field.put("Report_Type", "METAR");
        }
        unit.put("Report_Type", "" );

        // all patterns from this point are embeded in spaces

        //	$stn_name = $1 if( s#^(\w4) ## ) ;
        m = p.p_station.matcher(report);
        if (m.find()) {
            field.put("Station", m.group(1));
            unit.put("Station", "" );
            report = m.replaceFirst(" ");
        } else {
            field = null;
            return;
        }
        // get day hour minute 
        //	if( s#(\d2)(\d2)(\d2)Z## )
        m = p.p_ddhhmmZ.matcher(report);
        if (m.find()) {
            field.put("Day", m.group(1));
            unit.put("Day", "" );
            field.put("Hour", m.group(2));
            unit.put("Hour", "" );
            field.put("Minute", m.group(3));
            unit.put("Minute", "" );
            report = m.replaceFirst(" ");
        } else {
            field = null;
            return;
        }

        // skip NIL reports
        if (p.p_NIL.matcher(report).find()) {
            field = null;
            return;
        }

        m = p.p_COR.matcher(report);
        if (m.find()) {
            report = m.replaceFirst(" ");
        }

        //	$AUTO = 1 if( s#AUTO\s+## ) ;
        m = p.p_AUTOS.matcher(report);
        if (m.find()) {
            field.put("AUTOS", "yes");
            unit.put("AUTOS", "" );
            report = m.replaceFirst(" ");
        }

        // get wind direction and speed
        //	if( s#(E|W|N|S)?(\d3|VRB)(\d2,3)(G)?(\d2,3)?(KMH|KT|MPS|MPH)\s+## )
        m = p.p_wind_direction_speed.matcher(report);
        if (m.find()) {
            //	if( $2 eq "VRB" )
            if (m.group(2).equals("VRB")) {
                if( m.group(1) == null ) {
                    field.put("Variable_Wind_direction", "" );
                } else {
                    field.put("Variable_Wind_direction", m.group(1));
                }
                unit.put("Variable_Wind_direction", "" );
            } else {
                // $DIR = $2 ;
                field.put("Wind_Direction", m.group(2));
                unit.put("Wind_Direction", "degrees" );
            }
            //	$SPD = $3 ;
            field.put("Wind_Speed", m.group(3));
            //	$GUST = $5 if( $4 eq "G" ) ;
            if (m.group(4) != null && m.group(4).equals("G")) {
                field.put("Wind_Gust", m.group(5));
            }
            //		$UNITS = $6 ; need work  if units != KT
            unit.put("Wind_Speed", m.group(6));
            unit.put("Wind_Gust", m.group(6));
            report = m.replaceFirst(" ");
        }
        // get min|max wind direction
        //	if( s#^(\d3)V(\d3)\s+## )
        m = p.p_min_max_wind_dir.matcher(report);
        if (m.find()) {
            //		$DIRmin = $1 ;
            field.put("Wind_Direction_Min", m.group(1));
            unit.put("Wind_Direction_Min", "degrees" );
            //		$DIRmax = $2 ;
            field.put("Wind_Direction_Max", m.group(2));
            unit.put("Wind_Direction_Max", "degrees" );
            report = m.replaceFirst(" ");
        }

        // some reports use a place holder for visibility
        //		s#9999\s+## ;
        report = p.p_9999.matcher(report).replaceFirst(" ");

        // get visibility
        boolean done = false;

        //	$prevail_VIS_SM = 0.0 if( s#M1/4SM\s+|<1/4SM\s+## ) ;
        m = p.p_visibilitySM.matcher(report);
        if (m.find()) {
            field.put("Visibility", "0.0");
            unit.put("Visibility", "miles");
            report = m.replaceFirst(" ");
            done = true;
        }

        //	$prevail_VIS_KM = 0.0 if( s#M1/4KM\s+|<1/4KM\s+## ) ;
        if( ! done ) {
            
            m = p.p_visibilityKM.matcher(report);
            if (m.find()) {
                field.put("Visibility", "0.0");
                unit.put("Visibility", "kilometer");
                report = m.replaceFirst(" ");
                done = true;

            }

        }
        //	$plus_VIS_SM = 1 if( s#^P(\d1,3)SM\s+#$1SM # ) ;
        // just treat as Visibility by removing ^P
        //m = p.p_B_P.matcher(report);
        //if (m.find()) {
            //field.put("Plus_Visibility", "");
            //unit.put("Plus_Visibility", "miles");
            //report = m.replaceFirst(" ");
        //}

//	$plus_VIS_KM = 1 if( s#^P(\d1,3)KM\s+#$1KM # ) ;
        //m = p.p_B_P.matcher(report);
        //if (m.find()) {
            //field.put("Plus_Visibility_KM", "1");
            //unit.put("Plus_Visibility", "kilometer");
            //report = m.replaceFirst(" ");
        //}

        //	if( s#(\d1,4) (\d1,3)/(\d1,3)(SM|KM)\s+## )
        if( ! done ) {

            m = p.p_visibility1.matcher(report);
            if (m.find()) {
                //	$prevail_VIS_SM = $1 + ( $2 / $3 ) if( $4 eq "SM" ) ;
                var1 = Float.parseFloat(m.group(1));
                var2 = Float.parseFloat(m.group(2));
                var3 = Float.parseFloat(m.group(3));
                var1 = var1 + (var2 / var3);
                if (m.group(4).equals("SM")) {
                    field.put("Visibility", Float.toString(var1));
                    unit.put("Visibility", "miles");
                    // $prevail_VIS_KM = $1 + ( $2 / $3 ) if( $4 eq "KM" ) ;
                } else {
                    field.put("Visibility", Float.toString(var1));
                    unit.put("Visibility", "kilometer");
                }
                report = m.replaceFirst(" ");
                done = true;
            }

        }
        //	 else( s#^(\d1,3)/(\d1,3)(KM|SM)\s+## )
        if( ! done ) {

            m = p.p_visibility2.matcher(report);
            if (m.find()) {
                var1 = Float.parseFloat(m.group(1));
                var2 = Float.parseFloat(m.group(2));
                var1 = var1 / var2;
                //	 $prevail_VIS_SM = $1 / $2  if( $3 eq "SM" ) ;
                if (m.group(3).equals("SM")) {
                    field.put("Visibility", Float.toString(var1));
                    unit.put("Visibility", "miles");
			
                //	 $prevail_VIS_KM = $1 / $2  if( $3 eq "KM" ) ;
                } else {
                    field.put("Visibility", Float.toString(var1));
                    unit.put("Visibility", "kilometer");
                }
                report = m.replaceFirst(" ");
                done = true;
            }
        }
        //	else( s# P?(\d1,4)(KM|SM)\s+## )
        if( ! done ) {
              m = p.p_visibility3.matcher(report);
              if (m.find()) {
                  // $prevail_VIS_SM = $1 if( $2 eq "SM" ) ;
                  if (m.group(2).equals("SM")) {
                      field.put("Visibility", m.group(1));
                      unit.put("Visibility", "miles");
                     // $prevail_VIS_KM = $1 if( $2 eq "KM" ) ;
                  } else {
                      field.put("Visibility", m.group(1));
                      unit.put("Visibility", "kilometer");
                  }
                  report = m.replaceFirst(" ");
                  done = true;
              }
        }
        if( ! done ) {
            //	else( s# (\d4)((NE)|(NW)|(SE)|(SW)|(N)|(S)|(E)|(W))\s+## )
            m = p.p_visibility_direction.matcher(report);
            if (m.find()) {
            //	$prevail_VIS_M = $1 ;
                field.put("Visibility", m.group(1));
                unit.put("Visibility", "meters");
                // $VIS_dir = $2 ;
                field.put("Visibility_Direction", m.group(2));
                unit.put("Visibility_Direction", "");
                report = m.replaceFirst(" ");
                done = true;
             }
        }

        // 	clear air
        //	$CAVOK = 1 if( s#CAVOK\s+## ) ;
        m = p.p_CAVOKS.matcher(report);
        if (m.find()) {
            field.put("Clear_Air", "yes");
            unit.put("Clear_Air", "" );
            report = m.replaceFirst(" ");
        }

        // 	runway decoding here
        //	$RVRNO = 1 if( s#RVRNO\s+## ) ;
        m = p.p_RVRNO.matcher(report);
        if (m.find()) {
            //field.put("RVRNOS", "");
            field.put("RunwayReports", "No");
            //unit.put("RVRNOS", "");
            unit.put("RunwayReports", "");
            report = m.replaceFirst(" ");
        }

        for (int i = 0; i < 4; i++) {
            //	if( s# R(\d2)(R|L|C)?/(M|P)?(\d1,4)V?(M|P)?(\d1,4)?(FT|N|D)?\s+## )
            m = p.p_runway.matcher(report);
            if (m.find()) {
                //	$RV_designator[ $i ] = "$1$2" ;
                String RV = "RV" + Integer.toString(i +1);
                field.put(RV, m.group(1) );
                unit.put(RV, "");
                /*
                field.put(RV, m.group(1) + m.group(2));
                unit.put(RV, "");

                //	 $RV_above_max[ $i ] = 1
                // if( $3 eq "P" || $5 eq "P" ) ;
                if ((m.group(3) != null && m.group(3).equals("P")) || 
                    (m.group(5) != null && m.group(5).equals("P"))) {
                    field.put(RV + "_Above_Max", "");
                    unit.put(RV + "_Above_Max", "");
                }

                //	$RV_below_min[ $i ] = 1
                //	if( $3 eq "M" || $5 eq "M" ) ;
                if ((m.group(3) != null && m.group(3).equals("M")) || 
                    (m.group(5) != null && m.group(5).equals("M"))) {
                    field.put(RV + "_Below_Min", "");
                    unit.put(RV + "_Below_Min", "");
                }

                //			$RV_vrbl[ $i ] = 1 if( $6 ne "" ) ;
                //			if( $RV_vrbl[ $i ] )
                //				$RV_min[ $i ] = $4 * 1;
                //				$RV_max[ $i ] = $6 * 1;
                if (m.group(6) != null) {
                    field.put(RV + "_Vrbl", "");
                    unit.put(RV + "_Vrbl", "");
                    field.put(RV + "_Min", m.group(4));
                    unit.put(RV + "_Min", "feet");
                    field.put(RV + "_Max", m.group(6));
                    unit.put(RV + "_Max", "feet");
                } else {
                    //				$RV_visRange[ $i ] = $4 * 1;
                    if(m.group(7) != null && m.group(7).equals( "FT")) {
                        field.put(RV + "_Visibility_Range", m.group(4));
                        unit.put(RV + "_Visibility_Range", "feet");
                    } else {
                        field.put(RV + "_Visibility_Range", m.group(4));
                        unit.put(RV + "_Visibility_Range", "");
                    }
                }
                */
                report = m.replaceFirst(" ");
            } else {
                break;
            }
        } // end runway decoding

        // 	Get weather conditions
        // 	code table 4678
        done = true;
        StringBuilder WX = new StringBuilder();
        // this is necessary cuz these patterns can match internally in fields later 
        // in the report so the ob must start with a blank
        //report = " "+ report;
        for (int i = 0; i < 4; i++) {
            // if( s#(\+|-|VC|PR| )(MI|BC|DR|BL|SH|TS|FZ)?(DZ|RA|SN|SG|IC|PE|PL|GR|GS|UP)## )
            m = WeatherPrecip.matcher(report);
            //System.out.println( "before if  report ="+ report );
            if (m.find()) {
                done = false;
                //System.out.println( "after if report ="+ report );
                
                //if (! m.group(1).equals( " " )) {
                    WX.append( m.group(1) );
                //}
                if (m.group(2) != null) {
                    WX.append( m.group(2) );
                }
                WX.append( m.group(3) );
                report = m.replaceFirst(" ");
            }
            //  if( s#(\+|-|VC|PR| )(MI|BC|DR|BL|SH|TS|FZ)?(BR|FG|FU|VA|DU|SA|HZ|PY)## )
            m = WeatherObs.matcher(report);
            if (m.find()) {
                done = false;
                //System.out.println( "after if report ="+ report );
                
                //if (! m.group(1).equals( " " )) {
                    WX.append( m.group(1) );
                //}
                if (m.group(2) != null) {
                    WX.append( m.group(2) );
                }
                WX.append( m.group(3) );
                report = m.replaceFirst(" ");
            }
            // if( s#(\+|-|VC|PR| )(MI|BC|DR|BL|SH|TS|FZ)?(PO|SQ|FC|SS|DS)## )
            m = WeatherOther.matcher(report);
            if (m.find()) {
                done = false;
                //System.out.println( "after if report ="+ report );
                
                //if (! m.group(1).equals( " " )) {
                    WX.append( m.group(1) );
                //}
                if (m.group(2) != null) {
                    WX.append( m.group(2) );
                }
                WX.append( m.group(3) );
                report = m.replaceFirst(" ");
            }
            if( done )
                break;
            done = true;
            WX.append( " " );
        }
        if (WX.length() > 0) {
            WX.setLength( WX.length() -1 );
            field.put("Weather", WX.toString() );
            unit.put("Weather", "");
        }
        //System.out.println( "after if report ="+ report );


// 	Interpret cloud conditions
//	$cloud_type[ 0 ] = $1 if( s#(CLR|SKC)\s+## ) ;
        m = p.p_CLR_or_SKC.matcher(report);
        if (m.find()) {
            field.put("Cloud_Type", m.group(1));
            unit.put("Cloud_Type", "");
            report = m.replaceFirst(" ");
        }
//	$vert_VIS = cloud_hgt2_meters( $1 ) if( s#^VV(\d3)\s+## ) ;
        m = p.p_vertical_VIS.matcher(report);
        if (m.find()) {
            field.put("Vertical_Visibility", cloud_hgt2_meters(m.group(1)));
            unit.put("Vertical_Visibility", "meters" );
            report = m.replaceFirst(" ");
        }

//	for( $i = 0; $i < 6; $i++ )
        for (int i = 0; i < 6; i++) {
// 	cloud layers up to 6
//		if( s#^(\+|-)?(OVC|SCT|FEW|BKN)(\d3)(\w1,3)?\s+## )
            m = p.p_cloud_cover.matcher(report);
            if (m.find()) {
                String cloud = "Cloud_Layer_" + Integer.toString(i + 1);
//			$cloud_type[ $i ] = padstr( "$1$2", 4 ) ;
                if (m.group(1) == null) {
                    field.put(cloud + "_Type", m.group(2));
                    unit.put(cloud + "_Type", "");
                } else {
                    field.put(cloud + "_Type", m.group(1) + m.group(2));
                    unit.put(cloud + "_Type", "");
                }
//			$cloud_hgt[ $i ] = $3 * 100 ;
                field.put(cloud + "_Height", Integer.toString(Integer.parseInt(m.group(3)) * 100));
                unit.put(cloud + "_Height", "feet");

//			$cloud_meters[ $i ] = cloud_hgt2_meters( $3 ) ;
                field.put(cloud + "_Height", cloud_hgt2_meters(m.group(3)));
                unit.put(cloud + "_Height", "meters");
//			$cloud_phenom[ $i ] = padstr( $4, 4 ) if( $4 ) ;
                if (m.group(4) != null) {
                    field.put(cloud + "_Phenom", m.group(4));
                    unit.put(cloud + "_Phenom", "");
                }
                report = m.replaceFirst(" ");
            } else {
                break;
            }
        } // end clouds

        // 	Temperature and Dew Point, try temp tenths first
        float air_temperature = -999;
        float dew_point_temperature = -999;
        //	if( s#T(0|1)(\d3)(0|1)?(\d3)?\s+## )
        if( remark != null)
            m = Temperature_tenths.matcher(remark);

        if ( remark != null && m.find() ) {
            //		if( $1 == 0 )
            //			$T_tenths = 0.1 * $2 ;
            //	 	else
            //			$T_tenths = -0.1 * $2 ;
            //
            air_temperature = (float)(Float.parseFloat(m.group(2)) * .1);
            if (m.group(1).equals("1")) {
                //T = "-" + T;
                air_temperature *= -1;
            }
            field.put("Temperature", Float.toString( air_temperature ) );
            unit.put("Temperature", "Celsius");

            //		if( defined( $3 ) && $3 == 0 )
            //			$TD_tenths = 0.1 * $4 ;
            //	 	elsif( defined( $3 ) && $3 == 1 )
            //			$TD_tenths = -0.1 * $4 ;
            //
            if (m.group(3) != null) {
                dew_point_temperature = (float)(Float.parseFloat(m.group(4)) * .1);
                //			$TD *= -1 if( $3 ) ;
                if (m.group(3).equals("1")) {
                    //TD = "-" + TD;
                    dew_point_temperature *= -1;
                }
                field.put("DewPoint", Float.toString( dew_point_temperature ));
                unit.put("DewPoint", "Celsius");
            }
            remark = m.replaceFirst(" ");
        } else { // check for coarse temperature
            // 	get temperature and dew point
            //	if( s#^(M)?(\d2)/(M)?(\d2)?\s+## )
            m = Temperature.matcher(report);
            if (m.find()) {
                //		$T = $2 ;
                //String T = m.group(2);
                air_temperature = Float.parseFloat(m.group(2));
                //		$T *= -1 if( $1 ) ;
                //if(  m.group( 1 ).equals( "M" )  )
                if (m.group(1) != null) {
                    //T = "-" + T;
                    air_temperature *= -1;
                }
                field.put("Temperature", Float.toString( air_temperature ) );
                unit.put("Temperature", "Celsius");
                //		$TD = $4 if( defined( $4 ) ) ;
                if (m.group(4) != null) {
                    dew_point_temperature = Float.parseFloat(m.group(4));
                    //			$TD *= -1 if( $3 ) ;
                    if (m.group(3) != null) {
                        //TD = "-" + TD;
                       dew_point_temperature *= -1;
                    }
                    field.put("DewPoint", Float.toString( dew_point_temperature ));
                    unit.put("DewPoint", "Celsius");
                }
                report = m.replaceFirst(" ");
            } // end T and TD
        }

// 	get Altimeter settings
//	if( s#^(A|Q)(\d4\.?\d?)\s+## )
        m = p.p_altimeter.matcher(report);
        if (m.find()) {
//		if( $1 eq "A" )
            if (m.group(1).equals("A")) {
//			$inches_ALTIM = $2 * 0.01 ;
                field.put("Altimeter", Double.toString(Float.parseFloat(m.group(2)) * 0.01));
                unit.put("Altimeter", "inches");
            } else {
//			$hectoPasc_ALTIM = $2 ;
                field.put("Altimeter", m.group(2));
                unit.put("Altimeter", "hectopascal");
            }
            report = m.replaceFirst(" ");
        }

//	$NOSIG = 1 if( s#NOSIG## ) ;
        m = p.p_NOSIG.matcher(report);
        if (m.find()) {
            field.put("No_Weather", "yes");
            unit.put("No_Weather", "");
            report = m.replaceFirst(" ");
        }


        // 	check for remarks or done
        if( remark == null)
            return;

/*
        m = p.p_REMARKS.matcher(remark);
        if (m.find()) {
            remark = m.replaceFirst(" ");
        } else {
            if(remark.length() > 0)
                field.put("Plain_Language_remarks", remark);
                unit.put("Plain_Language_remarks", "");
            //return metar;
        }
*/
        // process remarks now, looking for most used ones first

        // get Automated reports
        //	 if( s#(A01|A01A|A02|A02A|AO1|AO1A|AO2|AO2A|AOA)\s+## ) ;
        m = p.p_automatic_report.matcher(remark);
        if (m.find()) {
            field.put("Automatic_Report", m.group(1));
            unit.put("Automatic_Report", "");
            remark = m.replaceFirst(" ");
        }

        //	check if no more info in report
        if (p.p_spaces.matcher(remark).matches()) {
            return;
        }

        // Sea-Level presure not available
        //$SLPNO = 1 if( s#SLPNO\s+## ) ;
        m = p.p_SLPNO.matcher(remark);
        if (m.find()) {
            //field.put("SLPNO", "yes");
            //unit.put("SLPNO", "");
            remark = m.replaceFirst(" ");
        }

        //if( s#SLP\s?(\d3)\s+## )
        m = p.p_SLP.matcher(remark);
        if (m.find()) {
            float slp;
            //	if( $1 >= 550 )
            if (Integer.parseInt(m.group(1)) >= 550) {
                // $SLP = $1 / 10. + 900. ;
                field.put("Sea_Level_Pressure", Double.toString(Float.parseFloat(m.group(1)) * 0.1 + 900));

            } else {
                // $SLP =  $1 / 10. + 1000. ;
                field.put("Sea_Level_Pressure", Double.toString(Float.parseFloat(m.group(1)) * 0.1 + 1000));
            }
            unit.put("Sea_Level_Pressure", "hectopascal");
            remark = m.replaceFirst(" ");
        }
        //	check if no more info in report
        if (p.p_spaces.matcher(remark).matches()) {
            return;
        }

        // 	Hourly precipitation amount
        //	$PRECIP_hourly = $1 / 100 if( s#P ?(\d1,5)\s+## ) ;
        m = p.p_hourly_precip.matcher(remark);
        if (m.find()) {
            field.put("Hourly_Precipitation", Double.toString(Float.parseFloat(m.group(1)) * .01));
            unit.put("Hourly_Precipitation", "inches");
            remark = m.replaceFirst(" ");
        }

        //	check if no more info in report
        if (p.p_spaces.matcher(remark).matches()) {
            return;
        }

        // precipitation sensor not working  PWINO
        //$PWINO = 1 if( s#PWINO\s+## ) ;
        m = p.p_PWINO.matcher(remark);
        if (m.find()) {
            //field.put("PWINO", "");
            //unit.put("PWINO", "");
            field.put("PRECIP_sensor_not working", "");
            unit.put("PRECIP_sensor_not working", "");
            remark = m.replaceFirst(" ");
        }

        //	check if no more info in report
        if (p.p_spaces.matcher(remark).matches()) {
            return;
        }

        // 	Lightning detection sensor not working  TSNO
        //	$TSNO = 1 if( s#TSNO\s+## ) ;
        m = p.p_TSNO.matcher(remark);
        if (m.find()) {
            //field.put("TSNO", "yes");
            //unit.put("TSNO", "");
            field.put("Lightning_sensor_not_working", "");
            unit.put("Lightning_sensor_not_working", "");
            remark = m.replaceFirst(" ");
        }

        //	check if no more info in report
        if (p.p_spaces.matcher(remark).matches()) {
            return;
        }

        // 	get Tornado data if present
        //	if( s#(TORNADO\w0,2|WATERSPOUTS*|FUNNEL CLOUDS*)\s+## )
        m = p.p_tornado.matcher(remark);
        if (m.find()) {
            field.put("TornadicType", m.group(1));
            unit.put("TornadicType", "");
            remark = m.replaceFirst(" ");
            // if( s#(B|E)(\d\d)(\d\d)?\s+## )
            m = p.p_tornadoTime.matcher(remark);
            if (m.find()) {
                String time;
                String units;
                if(m.group(2) == null ) {
                    time = m.group(3);
                    units = "mm";
                } else {
                    time = m.group(2) + m.group(3);
                    units = "hhmm";
                }
                // if( $1 eq "B" )
                if (m.group(1).equals("B")) {
                    // $BTornadic_hh = $2 ;
                    // $BTornadic_mm = $3 ;
                    field.put("Begin_Tornado", time);
                    unit.put("Begin_Tornado", units);
                } else {
                     // $ETornadic_hh = $2 ;
                     //	 $ETornadic_mm = $3 if( defined( $3 ) ) ;
                    field.put("End_Tornado", time);
                    unit.put("End_Tornado", units);
                }
                remark = m.replaceFirst(" ");
            }
            // $TornadicLOC = padstr( $1, 10 )
            //	 if( s#^(DSNT|VCY STN|VC STN|VCY|VC)\s+## ) ;
            m = p.p_tornadoLocation.matcher(remark);
            if (m.find()) {
                field.put("Tornado_Location", m.group(1));
                unit.put("Tornado_Location", "");
                remark = m.replaceFirst(" ");
            }

             // $TornadicDIR = padstr( $1, 2 )
            //	if( s#^(NE|NW|SE|SW|N|S|E|W)\s+## ) ;
            m = p.p_tornadoDirection.matcher(remark);
            if (m.find()) {
                field.put("Tornado_Direction", m.group(1));
                unit.put("Tornado_Direction", "");
                remark = m.replaceFirst(" ");
            }
        } // end tornado

// 	get Peak winds
//	if( s#PK WND (\d3)(\d1,3)/(\d\d)?(\d\d)\s+## )
        m = p.p_peakWind.matcher(remark);
        if (m.find()) {
//		$PKWND_dir = $1 ;
            field.put("Peak_Wind_Direction", m.group(1));
            unit.put("Peak_Wind_Direction", "degrees");
//		$PKWND_spd = $2 ;
            field.put("Peak_Wind_Speed", m.group(2));
            field.put("Peak_Wind_Speed", "knots");
//		$PKWND_hh = $3 if( defined( $3 ) ) ;
//		$PKWND_mm = $4 ;
            field.put("Peak_Wind_Time", m.group(3) + m.group(4));
            unit.put("Peak_Wind_Time", "");
            remark = m.replaceFirst(" ");
        }

// 	get Wind shift
//	if( s#WSHFT (\d\d)?(\d\d)\s+## )
        m = p.p_windShift.matcher(remark);
        if (m.find()) {
//		$WshfTime_hh = $1 if( defined( $1 ) );
//		$WshfTime_mm = $2 ;
            field.put("Wind_Shift", m.group(1) + m.group(2));
            unit.put("Wind_Shift", "");
            remark = m.replaceFirst(" ");
        }

// 	get FROPO ( wind shift because of frontal passage )
//	$Wshft_FROPA = 1 if( s#FROPA\s+## ) ;
        m = p.p_FROPA.matcher(remark);
        if (m.find()) {
            field.put("Wind_Shift_Frontal_Passage", "yes");
            unit.put("Wind_Shift_Frontal_Passage", "");
            remark = m.replaceFirst(" ");
        }

// 	Tower visibility
//	if( s#TWR (VIS|VSBY) (\d1,3) (\d1,2)/(\d1,2)\s+## ) 
//	$VIS_TWR = $2 + ( $3 / $4 ) ;
        m = p.p_towerVisibility1.matcher(remark);
        if (m.find()) {
            var1 = Float.parseFloat(m.group(2));
            var2 = Float.parseFloat(m.group(3));
            var3 = Float.parseFloat(m.group(4));
            var1 = var1 + (var2 / var3);
            field.put("Tower_Visibility", Float.toString(var1));
            unit.put("Tower_Visibility", "miles");
            remark = m.replaceFirst(" ");
// 	elsif( s#TWR (VIS|VSBY) (\d1,2)/(\d1,2)\s+## ) 
//		$VIS_TWR = ( $2 / $3 ) ;
        } else {
            m = p.p_towerVisibility2.matcher(remark);
            if (m.find()) {
                var1 = Float.parseFloat(m.group(2));
                var2 = Float.parseFloat(m.group(3));
                var1 = var1 / var2;
                field.put("Tower_Visibility", Float.toString(var1));
                unit.put("Tower_Visibility", "miles");
                remark = m.replaceFirst(" ");
// 		elsif( s#TWR (VIS|VSBY) (\d1,3)\s+## ) 
//			$VIS_TWR = $2 ;
            } else {
                m = p.p_towerVisibility3.matcher(remark);
                if (m.find()) {
                    field.put("Tower_Visibility", m.group(2));
                    unit.put("Tower_Visibility", "miles");
                    remark = m.replaceFirst(" ");
                }
            }
        }
// 	Surface visibility
//	if( s#SFC (VIS|VSBY) (\d1,3) (\d1,2)/(\d1,2)\s+## ) 
//	$VIS_SFC = $2 + ( $3 / $4 ) ;
        m = p.p_surfaceVisibility1.matcher(remark);
        if (m.find()) {
            var1 = Float.parseFloat(m.group(2));
            var2 = Float.parseFloat(m.group(3));
            var3 = Float.parseFloat(m.group(4));
            var1 = var1 + (var2 / var3);
            field.put("Surface_Visibility", Float.toString(var1));
            unit.put("Surface_Visibility", "miles");
            remark = m.replaceFirst(" ");
// 		elsif( s#SFC (VIS|VSBY) (\d1,2)/(\d1,2)\s+## ) 
//			$VIS_SFC = ( $2 / $3 ) ;
        } else {
            m = p.p_surfaceVisibility2.matcher(remark);
            if (m.find()) {
                var1 = Float.parseFloat(m.group(2));
                var2 = Float.parseFloat(m.group(3));
                var1 = var1 / var2;
                field.put("Surface_Visibility", Float.toString(var1));
                unit.put("Surface_Visibility", "miles");
                remark = m.replaceFirst(" ");
// 		elsif( s#SFC (VIS|VSBY) (\d1,3)\s+## ) 
//			$VIS_SFC = $2 ;
            } else {
                m = p.p_surfaceVisibility3.matcher(remark);
                if (m.find()) {
                    field.put("Surface_Visibility", m.group(2));
                    unit.put("Surface_Visibility", "miles");
                    remark = m.replaceFirst(" ");
                }
            }
        }
// 	Variable visibility
//	if( s#(VIS|VSBY) (\d1,3) (\d1,2)/(\d1,2)V(\d1,3) (\d1,2)/(\d1,2)\s+## ) 
//		$VISmin = $2 + ( $3 / $4 ) ;
//		$VISmax = $5 + ( $6 / $7 ) ;
        m = p.p_variableVisibility1.matcher(remark);
        if (m.find()) {
            var1 = Float.parseFloat(m.group(2));
            var2 = Float.parseFloat(m.group(3));
            var3 = Float.parseFloat(m.group(4));
            var1 = var1 + (var2 / var3);
            field.put("Variable_Visibility_Min", Float.toString(var1));
            unit.put("Variable_Visibility_Min", "miles");
            var1 = Float.parseFloat(m.group(5));
            var2 = Float.parseFloat(m.group(6));
            var3 = Float.parseFloat(m.group(7));
            var1 = var1 + (var2 / var3);
            field.put("Variable_Visibility_Max", Float.toString(var1));
            unit.put("Variable_Visibility_Max", "miles");
            remark = m.replaceFirst(" ");
//	 elsif( s#(VIS|VSBY) (\d1,3)V(\d1,3) (\d1,2)/(\d1,2)\s+## ) 
//		$VISmin = $2 ;
//		$VISmax = $3 + ( $4 / $5 ) ;
        } else {
            m = p.p_variableVisibility2.matcher(remark);
            if (m.find()) {
                field.put("Variable_Visibility_Min", m.group(2));
                unit.put("Variable_Visibility_Min", "miles");
                var1 = Float.parseFloat(m.group(2));
                var2 = Float.parseFloat(m.group(3));
                var3 = Float.parseFloat(m.group(4));
                var1 = var1 + (var2 / var3);
                field.put("Variable_Visibility_Max", Float.toString(var1));
                unit.put("Variable_Visibility_Max", "miles");
                remark = m.replaceFirst(" ");
// 		elsif( s#(VIS|VSBY) (\d1,2)/(\d1,2)V(\d1,3) (\d1,2)/(\d1,2)\s+## ) 
//			$VISmin = ( $2 / $3 ) ;
//			$VISmax = $4 + ( $5 / $6 ) ;
            } else {
                m = p.p_variableVisibility3.matcher(remark);
                if (m.find()) {
                    var1 = Float.parseFloat(m.group(2));
                    var2 = Float.parseFloat(m.group(3));
                    var1 = var1 / var2;
                    field.put("Variable_Visibility_Min", Float.toString(var1));
                    unit.put("Variable_Visibility_Min", "miles");
                    var1 = Float.parseFloat(m.group(2));
                    var2 = Float.parseFloat(m.group(3));
                    var3 = Float.parseFloat(m.group(4));
                    var1 = var1 + (var2 / var3);
                    field.put("Variable_Visibility_Max", Float.toString(var1));
                    unit.put("Variable_Visibility_Max", "miles");
                    remark = m.replaceFirst(" ");
// 			elsif( s#(VIS|VSBY) (\d1,3) (\d1,2)/(\d1,2)V(\d1,3)\s+## ) 
//				$VISmin = $2 + ( $3 / $4 ) ;
//				$VISmax = $5 ;
                } else {
                    m = p.p_variableVisibility4.matcher(remark);
                    if (m.find()) {
                        var1 = Float.parseFloat(m.group(2));
                        var2 = Float.parseFloat(m.group(3));
                        var3 = Float.parseFloat(m.group(4));
                        var1 = var1 + (var2 / var3);
                        field.put("Variable_Visibility_Min", Float.toString(var1));
                        unit.put("Variable_Visibility_Min", "miles");
                        field.put("Variable_Visibility_Max", m.group(5));
                        unit.put("Variable_Visibility_Max", "miles");
                        remark = m.replaceFirst(" ");
// 				elsif( s#(VIS|VSBY) (\d1,3)V(\d1,3)\s+## ) 
//					$VISmin = $2 ;
//					$VISmax = $3 ;
                    } else {
                        m = p.p_variableVisibility5.matcher(remark);
                        if (m.find()) {
                            field.put("Variable_Visibility_Min", m.group(2));
                            unit.put("Variable_Visibility_Min", "miles");
                            field.put("Variable_Visibility_Max", m.group(3));
                            unit.put("Variable_Visibility_Max", "miles");
                            remark = m.replaceFirst(" ");
//	 				elsif( s#(VIS|VSBY) (\d1,2)/(\d1,2)V(\d1,3)\s+## ) 
//						$VISmin = ( $2 / $3 ) ;
//						$VISmax = $4 ;
                        } else {
                            m = p.p_variableVisibility6.matcher(remark);
                            if (m.find()) {
                                var1 = Float.parseFloat(m.group(2));
                                var2 = Float.parseFloat(m.group(3));
                                var1 = var1 / var2;
                                field.put("Variable_Visibility_Min", Float.toString(var1));
                                unit.put("Variable_Visibility_Min", "miles");
                                field.put("Variable_Visibility_Max", m.group(4));
                                unit.put("Variable_Visibility_Max", "miles");
                                remark = m.replaceFirst(" ");
                            }
                        }
                    }
                }
            }
        } // end variableVisibility

// 	Second site visiblity
//	if( s#(VIS|VSBY) (\d1,3) (\d1,2)/(\d1,2) (RY\d1,2)\s+## ) 
//		$VIS_2ndSite = $2 + ( $3 / $4 ) ;
//		$VIS_2ndSite_LOC = padstr( $5, 10 ) ;
        m = p.p_Visibility2ndSite1.matcher(remark);
        if (m.find()) {
            var1 = Float.parseFloat(m.group(2));
            var2 = Float.parseFloat(m.group(3));
            var3 = Float.parseFloat(m.group(4));
            var1 = var1 + (var2 / var3);
            field.put("Second_Site_Visibility", Float.toString(var1));
            unit.put("Second_Site_Visibility", "miles");
            field.put("Second_Site_Location", m.group(5));
            unit.put("Second_Site_Location", "");
            remark = m.replaceFirst(" ");
// 	elsif( s#(VIS|VSBY) (\d1,3) (RY\d1,2)\s+## ) 
//		$VIS_2ndSite = $2 ;
//		$VIS_2ndSite_LOC = padstr( $3, 10 ) ;
        } else {
            m = p.p_Visibility2ndSite2.matcher(remark);
            if (m.find()) {
                field.put("Second_Site_Visibility", m.group(2));
                unit.put("Second_Site_Visibility", "miles");
                field.put("Second_Site_Location", m.group(3));
                unit.put("Second_Site_Location", "");
                remark = m.replaceFirst(" ");
// 		elsif( s#(VIS|VSBY) (\d1,2)/(\d1,2) (RY\d1,2)\s+## ) 
//			$VIS_2ndSite = ( $2 / $3 ) ;
//			$VIS_2ndSite_LOC = padstr( $4, 10 ) ;
            } else {
                m = p.p_Visibility2ndSite3.matcher(remark);
                if (m.find()) {
                    var1 = Float.parseFloat(m.group(2));
                    var2 = Float.parseFloat(m.group(3));
                    var1 = var1 / var2;
                    field.put("Second_Site_Visibility", Float.toString(var1));
                    unit.put("Second_Site_Visibility", "miles");
                    field.put("Second_Site_Location", m.group(4));
                    unit.put("Second_Site_Location", "");
                    remark = m.replaceFirst(" ");
                }
            }
        } // end Second site visiblity

// 	Lightning data ( Occasional,Frequent,Continuous) and
//	(Cloud-Ground,In-Cloud,Cloud-Cloud,Cloud-Air)
//	if( s#(OCNL|FRQ|CNS) LTG\s?(CG|IC|CC|CA)\s?(DSNT|AP|VCY STN|VCNTY STN)?\s?(NE|NW|SE|SW|N|S|E|W)?\s+## )
        m = p.p_Lightning.matcher(remark);
        if (m.find()) {
//		$LTG_OCNL = 1 if( $1 eq "OCNL" ) ;
//		$LTG_FRQ = 1 if( $1 eq "FRQ" ) ;
//		$LTG_CNS = 1 if( $1 eq "CNS" ) ;
//		$LTG_CG = 1 if( $2 eq "CG" ) ;
//		$LTG_IC = 1 if( $2 eq "IC" ) ;
//		$LTG_CC = 1 if( $2 eq "CC" ) ;
//		$LTG_CA = 1 if( $2 eq "CA" ) ;
//		$LTG_DSNT = 1 if( $3 eq "DSNT" ) ;
//		$LTG_AP = 1 if( $3 eq "AP" ) ;
//		$LTG_VcyStn = 1 if( $3 eq "VCY STN" || $3 eq "VCNTY STN" ) ;
//		$LTG_DIR = padstr( $4, 2 ) if( defined( $4 ) ) ;
            field.put("Lightning", m.group(0));
            unit.put("Lightning", "");
            remark = m.replaceFirst(" ");
        } // end Lightning data

// 	get min/max for Variable Ceiling
//	if( s#CIG (\d1,4)V(\d1,4)\s+## ) 
//		$Ceiling_min = $1 ;
//		$Ceiling_max = $2 ;
        m = p.p_CIG.matcher(remark);
        if (m.find()) {
            field.put("Ceiling_Min", Integer.toString(Integer.parseInt(m.group(1)) * 100));
            unit.put("Ceiling_Min", "feet");
            field.put("Ceiling_Max", Integer.toString(Integer.parseInt(m.group(2)) * 100));
            unit.put("Ceiling_Max", "feet");
            remark = m.replaceFirst(" ");
        }

//	 ? about SKY condition at 2nd location
// 	get 2nd site ceiling and location
//	if( s#CIG (\d3) (RY\d1,2)\s+## ) 
//		$CIG_2ndSite_meters = $1 * 10 ;
//		$CIG_2ndSite_LOC = $2 ;
        m = p.p_CIG_RY.matcher(remark);
        if (m.find()) {
            var1 = Float.parseFloat(m.group(1)) * 10;
            field.put("Second_Site_Sky", Float.toString(var1));
            unit.put("Second_Site_Sky", "feet");
            field.put("Second_Site_Sky_Location", m.group(2));
            unit.put("Second_Site_Sky_Location", "");
            remark = m.replaceFirst(" ");
        }

// 	Presure falling rapidly
//	$PRESFR = 1 if( s#PRESFR/?\s+## ) ;
        m = p.p_PRESFR.matcher(remark);
        if (m.find()) {
            field.put("Pressure_Falling_Rapidly", "yes");
            unit.put("Pressure_Falling_Rapidly", "");
            remark = m.replaceFirst(" ");
        }

// 	Presure rising rapidly
//	$PRESRR = 1 if( s#PRESRR/?\s+## ) ;
        m = p.p_PRESRR.matcher(remark);
        if (m.find()) {
            field.put("Pressure_Rising_Rapidly", "yes");
            unit.put("Pressure_Rising_Rapidly", "");
            remark = m.replaceFirst(" ");
        }

// 	Sector visibility
//	if( s#(VIS|VSBY) (NE|NW|SE|SW|N|S|E|W)(\d1,3) (\d1,2)/(\d1,2)\s+## ) 
//		$SectorVIS_DIR = padstr( $2, 2 ) ;
//		$SectorVIS = $3 + ( $4 / $5 ) ;
        m = p.p_sectorVisibility1.matcher(remark);
        if (m.find()) {
            field.put("Sector_Visibility_Direction", m.group(2));
            unit.put("Sector_Visibility_Direction", "");
            var1 = Float.parseFloat(m.group(3));
            var2 = Float.parseFloat(m.group(4));
            var3 = Float.parseFloat(m.group(5));
            var1 = var1 + (var2 / var3);
            field.put("Sector_Visibility", Float.toString(var1));
            unit.put("Sector_Visibility", "miles");
            remark = m.replaceFirst(" ");
// 	elsif( s#(VIS|VSBY) (NE|NW|SE|SW|N|S|E|W) (\d1,2)/(\d1,2)\s+## ) 
//		$SectorVIS_DIR = padstr( $2, 2 ) ;
//		$SectorVIS = ( $3 / $4 ) ;
        } else {
            m = p.p_sectorVisibility2.matcher(remark);
            if (m.find()) {
                field.put("Sector_Visibility_Direction", m.group(2));
                unit.put("Sector_Visibility_Direction", "");
                var1 = Float.parseFloat(m.group(1));
                var2 = Float.parseFloat(m.group(2));
                var1 = var1 / var2;
                field.put("Sector_Visibility", Float.toString(var1));
                unit.put("Sector_Visibility", "miles");
                remark = m.replaceFirst(" ");
// 		elsif( s#(VIS|VSBY) (NE|NW|SE|SW|N|S|E|W)(\d1,3)\s+## ) 
//			$SectorVIS_DIR = padstr( $2, 2 ) ;
//			$SectorVIS = $3 ;
            } else {
                m = p.p_sectorVisibility3.matcher(remark);
                if (m.find()) {
                    field.put("Sector_Visibility_Direction", m.group(2));
                    unit.put("Sector_Visibility_Direction", "");
                    field.put("Sector_Visibility", m.group(3));
                    unit.put("Sector_Visibility", "miles");
                    remark = m.replaceFirst(" ");
                }
            }
        }

// 	Hailstone activity and size
//	if( s#GR M1/4\s+## ) 
//		$GR = 1 ;
//		$GRsize = 1 / 8 ;
        m = p.p_GR1.matcher(remark);
        if (m.find()) {
            field.put("Hailstone_Activity", "yes");
            unit.put("Hailstone_Activity", "");
            field.put("Hailstone_Size", "0.25");
            unit.put("Hailstone_Size", "");
            remark = m.replaceFirst(" ");
// 	elsif( s#GR (\d1,3) (\d1,2)/(\d1,2)\s+## ) 
//		$GR = 1 ;
//		$GRsize = $1 + ( $2 / $3 ) ;
        } else {
            m = p.p_GR2.matcher(remark);
            if (m.find()) {
                field.put("Hailstone_Activity", "yes");
                unit.put("Hailstone_Activity", "");
                var1 = Float.parseFloat(m.group(1));
                var2 = Float.parseFloat(m.group(2));
                var3 = Float.parseFloat(m.group(3));
                var1 = var1 + (var2 / var3);
                field.put("Hailstone_Size", Float.toString(var1));
                unit.put("Hailstone_Size", "");
                remark = m.replaceFirst(" ");
// 		elsif( s#GR (\d1,2)/(\d1,2)\s+## ) 
//			$GR = 1 ;
//			$GRsize = ( $1 / $2 ) ;
            } else {
                m = p.p_GR3.matcher(remark);
                if (m.find()) {
                    field.put("Hailstone_Activity", "yes");
                    unit.put("Hailstone_Activity", "");
                    var1 = Float.parseFloat(m.group(1));
                    var2 = Float.parseFloat(m.group(2));
                    var1 = var1 / var2;
                    field.put("Hailstone_Size", Float.toString(var1));
                    unit.put("Hailstone_Size", "");
// 			elsif( s#GR (\d1,3)\s+## ) 
//				$GR = 1 ;
//				$GRsize = $1 ;
                } else {
                    m = p.p_GR4.matcher(remark);
                    if (m.find()) {
                        field.put("Hailstone_Activity", "yes");
                        unit.put("Hailstone_Activity", "");
                        field.put("Hailstone_Size", m.group(1));
                        unit.put("Hailstone_Size", "");
                        remark = m.replaceFirst(" ");
                    }
                }
            }
        }
//	$GR = 1 if( s#GS\s+## ) ;
        m = p.p_GR.matcher(remark);
        if (m.find()) {
            field.put("Hailstone_Activity", "yes");
            unit.put("Hailstone_Activity", "");
            remark = m.replaceFirst(" ");
        }

// 	VIRGA activity
//	if( s#VIRGA (DSNT )?(NE|NW|SE|SW|N|S|E|W)?\s+## ) 
//		$VIRGA = 1 ;
//		$VIRGAdir = padstr( $2, 2 ) if( $2 ) ;
        m = p.p_VIRGA.matcher(remark);
        if (m.find()) {
            field.put("Virga_Activity", "yes");
            unit.put("Virga_Activity", "");
            field.put("Virga_Direction", m.group(2));
            unit.put("Virga_Direction", "");
            remark = m.replaceFirst(" ");
        }

// 	Surface-based Obscuring Phenomena  SfcObscuration weather conditions
// 	code table 4678
//	if( s#-X(VC|PR)?(MI|BC|DR|BL|SH|TS|FZ)?(DZ|RA|SN|SG|IC|PE|PL|GR|GS|UP)?(BR|FG|FU|VA|DU|SA|HZ|PY)?(PO|SQ|FC|SS|DS)?(\d)\s+## ) 
//		$SfcObscuration = padstr( "$1$2$3$4$5", 8 ) ;
//		$OctsSkyObscured = $6 ;
        m = p.p_obscuring.matcher(remark);
        if (m.find()) {
            String tmp = "";
            if (m.group(1) != null) {
                tmp = m.group(1);
            }
            if (m.group(2) != null) {
                tmp = m.group(2);
            }
            if (m.group(3) != null) {
                tmp = m.group(3);
            }
            if (m.group(4) != null) {
                tmp = m.group(4);
            }
            if (m.group(5) != null) {
                tmp = m.group(5);
            }
            if ( ! tmp.equals("")) {
                field.put("Surface_Obscuration", tmp );
                unit.put("Surface_Obscuration", "" );
            }
            field.put("OctsSkyObscured", m.group(6));
            unit.put("OctsSkyObscured", "");
            remark = m.replaceFirst(" ");
        }

// 	get Ceiling_est or Ceiling height
//	$CIGNO = 1 if( s#CIGNO\s+## ) ;
        m = p.p_CIGNO.matcher(remark);
        if (m.find()) {
            field.put("CIGNO", "yes");
            unit.put("CIGNO", "");
            remark = m.replaceFirst(" ");
        }
//	if( s#CIG(E)?(\d3)\s+## ) 
//		if( $1 eq "E" ) 
//			$Ceiling_est = $2 * 100 ;
//		 else 
//			$Ceiling = $2 * 100 ;
        m = p.p_CIG_EST.matcher(remark);
        if (m.find()) {
            String est = Integer.toString(Integer.parseInt(m.group(2)) * 100);
            if (m.group(1).equals("E")) {
                field.put("Ceiling_Estimate", est);
                unit.put("Ceiling_Estimate", "feet");
            } else {
                field.put("Ceiling", est);
                unit.put("Ceiling", "feet");
            }
            remark = m.replaceFirst(" ");
        }

// 	Variable Sky conditions
//	if( s#(FEW|SCT|BKN|OVC)(\d3)? V (FEW|SCT|BKN|OVC)\s+## ) 
//		$VrbSkyBelow = $1 ;
//		$VrbSkyLayerHgt = $2 * 100 if( defined( $2 ) ) ;
//		$VrbSkyAbove = $3 ;
        m = p.p_variableSky.matcher(remark);
        if (m.find()) {
            field.put("Variable_Sky_Below", m.group(1));
            unit.put("Variable_Sky_Below", "");
            //String est = Integer.toString(Integer.parseInt(m.group(2)) * 100);
            if (m.group(2) != null) {
                field.put("Variable_Sky_Height", Integer.toString(Integer.parseInt(m.group(2)) * 100));
                unit.put("Variable_Sky_Height", "feet");
            }
            field.put("Variable_Sky_Above", m.group(3));
            field.put("Variable_Sky_Above", "feet");
            remark = m.replaceFirst(" ");
        }

// 	Significant Cloud Types
//	if( s#(CB|CBMAM|TCU|ACC|SCSL|ACSL|ROTOR CLD|ROPE|ROPE CLD)\s+## ) 
//		$Sign_cloud = padstr( $1, 10 ) ;
//		$Sign_dist = padstr( $1, 10 ) 
//			if( s#^(VCNTY STN|VCY STN|VC STN|VCY|VC|DSNT|OMT)\s+## ) ;
//		$Sign_dir = padstr( "$1$2$3", 10 ) 
//			if( s#^(NE|NW|SE|SW|N|S|E|W)(\-| MOV )?(NE|NW|SE|SW|N|S|E|W)?/?\s+## ) ;
        m = p.p_significantCloud.matcher(remark);
        if (m.find()) {
            field.put("Significant_Cloud", m.group(1));
            unit.put("Significant_Cloud", "");
            remark = m.replaceFirst(" ");
            m = p.p_significantCloud1.matcher(remark);
            if (m.find()) {
                field.put("Significant_Cloud_Vicinity", m.group(1));
                unit.put("Significant_Cloud_Vicinity", "" );
                remark = m.replaceFirst(" ");
                m = p.p_significantCloud2.matcher(remark);
                if (m.find()) {
                    field.put("Significant_Cloud_Direction", m.group(1));
                    unit.put("Significant_Cloud_Direction", "");
                    remark = m.replaceFirst(" ");
                }
            }
        }

// 	Obscuring Phenomena Aloft
//	if( s#(VC|PR)?(MI|BC|DR|BL|SH|TS|FZ)?(DZ|RA|SN|SG|IC|PE|PL|GR|GS|UP)?(BR|FG|FU|VA|DU|SA|HZ|PY)?(PO|SQ|FC|SS|DS)? (FEW|SCT|BKN|OVC)(\d3)\s+## ) 
//		$ObscurAloft = padstr( "$1$2$3$4$5", 8 ) ;
//		$ObscurAloftSkyCond = $6 ;
//		$ObscurAloftHgt = $7 * 100 ;
//
        m = p.p_obscuringPhen.matcher(remark);
        if (m.find()) {
            String tmp = "";
            if (m.group(1) != null) {
                tmp = m.group(1);
            }
            if (m.group(2) != null) {
                tmp = m.group(2);
            }
            if (m.group(3) != null) {
                tmp = m.group(3);
            }
            if (m.group(4) != null) {
                tmp = m.group(4);
            }
            if (m.group(5) != null) {
                tmp = m.group(5);
            }
            if ( ! tmp.equals("")) {
                field.put("Surface_Phenomena", tmp );
                unit.put("Surface_Phenomena", "" );
            }
            field.put("Obscuring_Phenomena_Sky", m.group(6));
            unit.put("Obscuring_Phenomena_Sky", "");
            if (m.group(7) != null) {
                field.put("Obscuring_Phenomena_Sky_Height", Integer.toString(Integer.parseInt(m.group(7)) * 100));
                unit.put("Obscuring_Phenomena_Sky_Height", "feet");
            }
            remark = m.replaceFirst(" ");
        }

// 	Air craft mishap  ACFTMSHP
//	$ACFTMSHP = 1 if( s#\(?ACFT\s?MSHP\)?\s+## ) ;
        m = p.p_ACFT.matcher(remark);
        if (m.find()) {
            field.put("Air_craft_mishap", "yes");
            unit.put("Air_craft_mishap", "");
            remark = m.replaceFirst(" ");
        }

// 	No changes in weather conditions until next report  NOSPECI
//	$NOSPECI = 1 if( s#NOSPECI\s+## ) ;
        m = p.p_NOSPECI.matcher(remark);
        if (m.find()) {
            field.put(" No_changes_in_weather", "yes");
            unit.put(" No_changes_in_weather", "");
            remark = m.replaceFirst(" ");
        }

// 	This is first report of the day  FIRST
//	$FIRST = 1 if( s#FIRST\s+## ) ;
        m = p.p_FIRST.matcher(remark);
        if (m.find()) {
            field.put("First_Report_Today", "yes");
            unit.put("First_Report_Today", "");
            remark = m.replaceFirst(" ");
        }

// 	This is last report in observation coverage  LAST
//	$LAST = 1 if( s#LAST\s+## ) ;
        m = p.p_LAST.matcher(remark);
        if (m.find()) {
            field.put("Last_Report_Today", "yes");
            unit.put("Last_Report_Today", "");
            remark = m.replaceFirst(" ");
        }

        // 	Cloud Types
        //	if( s# 8/(\d|/)(\d|/)(\d|/)\s+# # )
        //		$Cloud_low = $1 ;
        //		$Cloud_medium = $2 ;
        //		$Cloud_high = $3 ;
        m = p.p_cloud_height.matcher(remark);
        if (m.find()) {
            if( ! m.group(1).equals( "/")) {
                field.put("Cloud_Low", m.group(1));
                unit.put("Cloud_Low", "");
            }
            if( ! m.group(2).equals( "/")) {
                field.put("Cloud_Medium", m.group(2));
                unit.put("Cloud_Medium", "");
            }
            if( ! m.group(3).equals( "/")) {
                field.put("Cloud_High", m.group(3));
                unit.put("Cloud_High", "");
            }
            remark = m.replaceFirst(" ");
        }
//
// 	Snow Increasing Rapidly   SNINCR
//	if( s#SNINCR (\d1,3)/(\d1,3)\s+## ) 
//		$SNINCR = $1 ;
//		$SNINCR_TotalDepth = $2 ;
        m = p.p_SNINCR.matcher(remark);
        if (m.find()) {
            field.put("Snow_Increasing_Rapidly", m.group(1));
            unit.put("Snow_Increasing_Rapidly", "");
            field.put("Snow_Increasing_Depth", m.group(2));
            unit.put("Snow_Increasing_Depth", "");
            remark = m.replaceFirst(" ");
        }
        //
        // 	Snow depth on ground
        //	if( s#4/(\d1,3)\s+# # )
        //		$SN_depth = $1 ;
        m = p.p_snowDepth.matcher(remark);
        if (m.find()) {
            field.put("Snow_Depth", m.group(1));
            unit.put("Snow_Depth", "inches");
            remark = m.replaceFirst(" ");
        }
//
//	 Water equivalent of snow on ground
//	$SN_waterequiv = $1 / 10 if( s# 933(\d3)\s+# # ) ;
        m = p.p_waterEquiv.matcher(remark);
        if (m.find()) {
            field.put("Water_Equivalent_of_Snow", Double.toString(Float.parseFloat(m.group(1)) * 0.1 ));
            unit.put("Water_Equivalent_of_Snow", "");
            remark = m.replaceFirst(" ");
        }

// 	Duration of sunshine
//	if( s# 98(\d1,3|///)\s+# # ) 
//		if( $1 eq "///" ) 
//			$SunSensorOut = 1 ;
//		 else 
//			$SunShineDur = $1 ;
        m = p.p_sunShine.matcher(remark);
        if (m.find()) {
            if( m.group(1).equals("///") ) {
                field.put("Sun_Sensor_Out", "yes");
                unit.put("Sun_Sensor_Out", "");
            } else {
                field.put("Sun_Sensor_Duration", m.group(1));
                unit.put("Sun_Sensor_Duration", "");
            }
            remark = m.replaceFirst(" ");
        }

        // 	Precipitation amount
        //	if( s# 6(\d4|////)\s+# # )
        //		$PRECIP_amt = $1 / 100 if( $1 ne "////" ) ;
        m = p.p_precipitation.matcher(remark);
        if (m.find()) {
            if( ! m.group(1).equals("////") ) {
                field.put("Precipitation_amount", Double.toString(Float.parseFloat(m.group(1)) * 0.01 ));
                unit.put("Precipitation_amount", "inches");
            }
            remark = m.replaceFirst(" ");
        }
        //
        // 	24 Hour Precipitation amount
        //	if( s# 7(\d4|////)\s+# # )
        //		$PRECIP_24_amt = $1 / 100 if( $1 ne "////" ) ;
        m = p.p_precipitation24.matcher(remark);
        if (m.find()) {
            if( ! m.group(1).equals("////") ) {
                field.put("Precipitation_amount_24Hours", Double.toString(Float.parseFloat(m.group(1)) * 0.01 ));
                unit.put("Precipitation_amount_24Hours", "inches");
            }
            remark = m.replaceFirst(" ");
        }
        //
        // 	Maximum Temperature
        //	if( s# 1(0|1|/)(\d3|///)\s+# # )
        //		$Tmax = $2 / 10 if( $2 ne "///" ) ;
        //		$Tmax *= -1.0 if( $1 == 1 ) ;
        //
        m = p.p_maxTemperature.matcher(remark);
        if (m.find()) {
            if( ! m.group(2).equals("///") ) {
                double maxtemp = Float.parseFloat(m.group(2));
                if( m.group(1).equals("1") ) {
                    maxtemp *= -0.1;
                } else if( m.group(1).equals("0") ) {
                    maxtemp *= 0.1;
                }
                field.put("Max_Temperature", Double.toString( maxtemp ) );
                unit.put("Max_Temperature", "Celsius");
            }
            remark = m.replaceFirst(" ");
        }

        // 	Minimum Temperature
        //	if( s# 2(0|1|/)(\d3|///)\s+# # )
        //		$Tmin = $2 / 10 if( $2 ne "///" ) ;
        //		$Tmin *= -1.0 if( $1 == 1 ) ;
        m = p.p_minTemperature.matcher(remark);
        if (m.find()) {
            if( ! m.group(2).equals("///") ) {
                double mintemp = Float.parseFloat(m.group(2));
                if( m.group(1).equals("1") ) {
                    mintemp *= -0.1;
                } else if( m.group(1).equals("0") ) {
                    mintemp *= 0.1;
                }
                field.put("Min_Temperature", Double.toString( mintemp ) );
                unit.put("Min_Temperature", "Celsius");
            }
            remark = m.replaceFirst(" ");
        }
        //
        // 	24-Hour Maximum and Minimum Temperature
        //	if( s# 4(0|1|/)(\d3|///)(0|1|/)(\d3|///)\s+# # )
        //		$Tmax24 = $2 / 10 if( $2 ne "///" ) ;
        //		$Tmax24 *= -1.0 if( $1 == 1 ) ;
        //		$Tmin24 = $4 / 10 if( $4 ne "///" ) ;
        //		$Tmin24 *= -1.0 if( $3 == 1 ) ;
        m = p.p_maxMinTemp24.matcher(remark);
        if (m.find()) {
            if( ! m.group(2).equals("///") ) {
                double maxtemp = Float.parseFloat(m.group(2));
                if( m.group(1).equals("1") ) {
                    maxtemp *= -0.1;
                } else if( m.group(1).equals("0") ) {
                    maxtemp *= 0.1;
                }
                field.put("Max_Temperature_24Hour", Double.toString( maxtemp ) );
                unit.put("Max_Temperature_24Hour", "Celsius");
            }
            if( ! m.group(4).equals("///") ) {
                double mintemp = Float.parseFloat(m.group(4));
                if( m.group(3).equals("1") ) {
                    mintemp *= -0.1;
                } else if( m.group(3).equals("0") ) {
                    mintemp *= 0.1;
                }
                field.put("Min_Temperature_24Hour", Double.toString( mintemp ) );
                unit.put("Min_Temperature_24Hour", "Celsius");
            }
            remark = m.replaceFirst(" ");
        }
        //
        // 	Presure Tendency
        //	if( s# 5(0|1|2|3|4|5|6|7|8)(\d3/?|///)\s+# # )
        //		$char_Ptend = $1 ;
        //		$Ptend = $2 / 10 if( $2 ne "///" ) ;
        //
        m = p.p_pressureTendency.matcher(remark);
        if (m.find()) {
            field.put("Presure_Tendency_char", m.group(1));
            unit.put("Presure_Tendency_char", "");
            if( ! m.group(2).equals("///") ) {
                field.put("Presure_Tendency", Double.toString(Float.parseFloat(m.group(2)) * 0.1 ));
                unit.put("Presure_Tendency", "hectopascals");
            }
            remark = m.replaceFirst(" ");
        }

// 	Freezing Rain sensor not working  FZRANO
//	$FZRANO = 1 if( s#FZRANO\s+## ) ;
        m = p.p_FZRANO.matcher(remark);
        if (m.find()) {
            field.put("Freezing_Rain_sensor_not_working", "yes");
            unit.put("Freezing_Rain_sensor_not_working", "");
            remark = m.replaceFirst(" ");
        }

// 	Tipping bucket rain gauge is inoperative.
//	$PNO = 1 if( s#PNO\s+## ) ;
        m = p.p_PNO.matcher(remark);
        if (m.find()) {
            field.put("Tipping_bucket_rain_gauge_inoperative", "yes");
            unit.put("Tipping_bucket_rain_gauge_inoperative", "");
            remark = m.replaceFirst(" ");
        }

// 	Maintenance is needed on system Indicator
//	$maintIndicator = 1 if( s#\$\s+## ) ;
        m = p.p_maintenace.matcher(remark);
        if (m.find()) {
            field.put("Maintenance_needed_on_system", "yes");
            unit.put("Maintenance_needed_on_system", "");
            remark = m.replaceFirst(" ");
        }

// 	Get Recent weather conditions with Beginning and Ending times, moved 
//	because the RE are too general and they match wrongly
// 	code table 4678
	for(int i = 0; i < 3; i++ ) {
            String RWX = "Recent_Weather_"+ Integer.toString( i +1 );
//	    if( s#(\+|-|VC|PR)?(MI|BC|DR|BL|SH|TS|FZ)?(DZ|RA|SN|SG|IC|PE|PL|GR|GS|UP)?(BR|FG|FU|VA|DU|SA|HZ|PY)?(PO|SQ|FC|SS|DS)?B(\d2,4)E(\d2,4)\s+## ) 
            m = p.p_recentWeather.matcher(remark);
            if (m.find()) {
//		$Recent_WX[ $i ] = padstr( "$1$2$3$4$5", 8 ) ;
                String tmp = "";
                if (m.group(1) != null) {
                    tmp = m.group(1);
                }
                if (m.group(2) != null) {
                    tmp = tmp + m.group(2);
                }
                if (m.group(3) != null) {
                    tmp = tmp + m.group(3);
                }
                if (m.group(4) != null) {
                    tmp = tmp + m.group(4);
                }
                if (m.group(5) != null) {
                    tmp = tmp + m.group(5);
                }
                if ( ! tmp.equals("")) {
                    field.put( RWX, tmp );
                    unit.put( RWX, "" );
                } else {
                    break;
                }
//		if( length( $6 ) == 5 ) 
//			$Recent_WX_Bhh[ $i ] = substr( $6, 1, 2 ) * 1 ;
//			$Recent_WX_Bmm[ $i ] = substr( $6, 3, 2 ) * 1 ;
//		elsif( length( $6 ) == 3 ) 
//			$Recent_WX_Bmm[ $i ] = substr( $6, 1, 2 ) * 1 ;
                field.put( RWX +"_Begin_Time", m.group(6) );
                unit.put( RWX +"_Begin_Time", "" );
//		if( length( $7 ) == 5 ) 
//			$Recent_WX_Ehh[ $i ] = substr( $7, 1, 2 ) * 1 ;
//			$Recent_WX_Emm[ $i ] = substr( $7, 3, 2 ) * 1 ;
//	   	elsif( length( $7 ) == 3 ) 
//			$Recent_WX_Emm[ $i ] = substr( $7, 1, 2 ) * 1 ;
                field.put( RWX +"_End_Time", m.group(7) );
                unit.put( RWX +"_End_Time", "" );
                remark = m.replaceFirst(" ");
//	    elsif( s#(\+|-|VC|PR)?(MI|BC|DR|BL|SH|TS|FZ)?(DZ|RA|SN|SG|IC|PE|PL|GR|GS|UP)?(BR|FG|FU|VA|DU|SA|HZ|PY)?(PO|SQ|FC|SS|DS)?(B|E)(\d2,4)\s+## ) 
            } else {
                m = p.p_recentWeather1.matcher(remark);
                if (m.find()) {
//		$Recent_WX[ $i ] = padstr( "$1$2$3$4$5", 8 ) ;
                    String tmp = "";
                    if (m.group(1) != null) {
                        tmp = m.group(1);
                    }
                    if (m.group(2) != null) {
                        tmp = tmp + m.group(2);
                    }
                    if (m.group(3) != null) {
                        tmp = tmp + m.group(3);
                    }
                    if (m.group(4) != null) {
                        tmp = tmp + m.group(4);
                    }
                    if (m.group(5) != null) {
                        tmp = tmp + m.group(5);
                    }
                    if ( ! tmp.equals("")) {
                        field.put( RWX, tmp );
                        unit.put( RWX, "" );
                    } else {
                        break;
                    }
//		if( $6 eq "B" && ( length( $7 ) == 4 )) 
//			$Recent_WX_Bhh[ $i ] = substr( $7, 0, 2 ) * 1 ;
//			$Recent_WX_Bmm[ $i ] = substr( $7, 2, 2 ) * 1 ;
//		 elsif( $6 eq "B" && ( length( $7 ) == 2 )) 
//			$Recent_WX_Bmm[ $i ] = substr( $7, 0, 2 ) * 1 ;
                    if( m.group(6).equals("B" ) ) {
                        field.put( RWX +"_Begin_Time", m.group(7) );
                        unit.put( RWX +"_Begin_Time", "" );
//		 elsif( $6 eq "E" && ( length( $7 ) == 4 )) 
//			$Recent_WX_Ehh[ $i ] = substr( $7, 0, 2 ) * 1 ;
//			$Recent_WX_Emm[ $i ] = substr( $7, 2, 2 ) * 1 ;
//		 elsif( $6 eq "E" && ( length( $7 ) == 2 )) 
//			$Recent_WX_Emm[ $i ] = substr( $7, 0, 2 ) * 1 ;
                     } else {
                        field.put( RWX +"_End_Time", m.group(7) );
                        unit.put( RWX +"_End_Time", "" );
		             }
                     remark = m.replaceFirst(" ");
	        } else {
//		last ;
                    break;
	        }
            }
        } // end for recent weather

// 	Plain Language remarks includes Volcanic eruptions
//	$PlainText = padstr( $1, 128 ) if( s#(\w.*)## ) ;
        m = p.p_plainText.matcher(remark);
        if (m.find()) {
            field.put("Plain_Language_remarks", m.group(1));
            unit.put("Plain_Language_remarks", "");
            remark = m.replaceFirst(" ");
        }

    } // end parseReport

// convert cloud height to  meters
    private String cloud_hgt2_meters(String height) {

        if (height.equals("999")) {
            return "30000";
        } else {
//		$meters = 30 * $height ;
            return Integer.toString(30 * Integer.parseInt(height));
        }
    } // end cloud_hgt2_meters

    /**
     * Used to return fields in Metar report.
     * @return LinkedHashMap
     **/
     public LinkedHashMap getFields() {
        return field;
     }

    /**
     * Used to return units of the fields in Metar report.
     * @return LinkedHashMap
     **/
     public HashMap getUnits() {
        return unit;
     }

    public static void main(String args[]) throws IOException {

        String report = null;
        //report = "KD07 150256Z AUTO 28005KT BR M08/M11 A3005 RMK AO2 SLP223 T10781111 21205 40051//// 50006 PWINO FZRANO";
        //report = "METAR K1V4 251254Z AUTO 01/01 A3002 RMK AO2 SLP172 P0001 T00110006 PWINO FZRANO TSNO ";
        //report = "KDEN 201453Z 35007KT 1/4SM R35L/5000V6000FT -DZ BR -SG -FZDZ OVC003 05/04 A2996 RMK 70155 53777 61111 8/53/";
        //report = "ROBB 201453Z 35007KT 1/4SM RMK AO1 21200 4/12 T00110006 SLPNO SLP067 P0000";
        //report = "ROBB 201453Z NVRB05G15KT 090V180 1/4SM AUTO RMK PWINO TSNO TORNADO B10 DSNT NE";
        report = "ROBB 201453Z NVRB05G15KT R26/0450V0650D RMK PWINO TSNO TORNADO B10 DSNT NE";
        //report = "KDEN 201453Z 35007KT 1/4SM R35L/5000V6000FT -DZ BR -SG -FZDZ OVC003 05/04 A2996 RMK CIG 003V006";
        //report = "KDEN 201553Z 01006KT 1/4SM R35L/4500VP6000FT  -DZ BR OVC003 05/04 A2996 RMK AO2 SFC VIS 3/4 CIG 003V006 SLP124 P0000 T00500044";
        //report = "SOCA 202000Z 10003KT 9999 VCSH FEW010CB SCT015TCU 27/26 Q1010 TEMPO 4000 CB & TCU AU S ET W";
        // Function References
        MetarParseReport func = new MetarParseReport();

        if( false && args.length == 1 ) {
            System.out.println( args[ 0 ] +" "+ args.length );
            // read reports from file to parse
            InputStream ios = new FileInputStream( args[ 0 ] );
            BufferedReader dataIS =
                new BufferedReader(new InputStreamReader(ios));

            //System.out.println("<report>");
            while (true) {
                report = dataIS.readLine();
                if (report == null) {
                    break;
                }
                System.out.println( report );
                func.parseReport(report);
                LinkedHashMap field = func.getFields();
                HashMap unit = func.getUnits();
                String key;

                if (field == null) {
                    System.out.println("return null Hash parse");
                    System.exit(1);
                }
                System.out.println("<report>");
                for( Iterator it = field.keySet().iterator(); it.hasNext(); ) {
                    key = (String) it.next();
                    //System.out.println(key + "\t\t" + (String) field.get(key));
                    System.out.println("\t<parameter name=\""+ key +"\" value=\""+ 
                   (String) field.get(key) +"\" units=\""+ (String) unit.get(key)
                       +"\" />" );
                }
            }

            System.out.println("</report>");
            return;
        }


        // else
        System.out.println( report );
        func.parseReport(report);
        LinkedHashMap field = func.getFields();
        HashMap unit = func.getUnits();
        String key;

        if (field == null) {
            System.out.println("return null Hash parse");
            System.exit(1);
        }
        System.out.println("<report>");
        for( Iterator it = field.keySet().iterator(); it.hasNext(); ) {
            key = (String) it.next();
            //System.out.println(key + "\t\t" + (String) field.get(key));
            System.out.println("\t<parameter name=\""+ key +"\" value=\""+ 
           (String) field.get(key) +"\" units=\""+ (String) unit.get(key)
           +"\" />" );
        }
        System.out.println("</report>");

    }

} // end MetarParseReport
