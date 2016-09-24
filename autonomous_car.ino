/*
 * Autonomous Car
 * by Simone Ramini.
 */

#include <SoftwareSerial.h>

// MOTORS
#define LEFT_MOTORS_DIRA_PIN  2
#define LEFT_MOTORS_DIRB_PIN  3
#define RIGHT_MOTORS_DIRA_PIN 4
#define RIGHT_MOTORS_DIRB_PIN 5

// ULTRASONIC SENSORS
#define FRONT_SONAR_TRIG_PIN A5
#define FRONT_SONAR_ECHO_PIN A4
#define RIGHT_SONAR_TRIG_PIN A3
#define RIGHT_SONAR_ECHO_PIN A2

// HC_05
#define BT_RX_PIN 6
#define BT_TX_PIN 7

#define MAX_OBSTACLE_DISTANCE 35 // cm
#define MAX_LATERAL_DISTANCE  10 // cm

#define SERIAL_PORT_BAUDRATE 9600  // baud
#define HC05_BAUDRATE        9600 // baud

#define DISTANCE_ARRAY_SIZE 5


typedef enum { 
  GO_FORWARD         = 0,
  GO_BACKWARD        = 1,
  TURN_LEFT          = 2,
  TURN_RIGHT         = 3,
  TURN_LEFT_REVERSE  = 4,
  TURN_RIGHT_REVERSE = 5,
  STOP               = 6,
  OVERTAKE_START     = 7,
  OVERTAKE_END       = 8
} state;


state car_state = STOP;
SoftwareSerial bt (BT_RX_PIN, BT_TX_PIN);

int obstacle_distance [DISTANCE_ARRAY_SIZE], lateral_distance [DISTANCE_ARRAY_SIZE];

void setup() {
  pinMode (LEFT_MOTORS_DIRA_PIN, OUTPUT);
  pinMode (LEFT_MOTORS_DIRB_PIN, OUTPUT);
  pinMode (RIGHT_MOTORS_DIRA_PIN, OUTPUT);
  pinMode (RIGHT_MOTORS_DIRB_PIN, OUTPUT);
  pinMode (FRONT_SONAR_TRIG_PIN, OUTPUT);
  pinMode (FRONT_SONAR_ECHO_PIN, INPUT);
  pinMode (RIGHT_SONAR_TRIG_PIN, OUTPUT);
  pinMode (RIGHT_SONAR_ECHO_PIN, INPUT);

  pinMode (BT_RX_PIN, INPUT);
  pinMode (BT_TX_PIN, OUTPUT);
  bt.begin (HC05_BAUDRATE);

  for (int i = 0; i < DISTANCE_ARRAY_SIZE; i++) obstacle_distance [i] = lateral_distance [i] = 1000;

  Serial.begin (SERIAL_PORT_BAUDRATE);
}

void loop() {
  int distance, i;
  boolean overtake_end, overtake_start;
  
  // STATUS RECEIVED
  if (bt.available ()) {
    switch ((char) bt.read ()) {
      case '0':
        car_state = GO_FORWARD;
        break;
      case '1':
        car_state = GO_BACKWARD;
        break;
      case '2':
        car_state = TURN_LEFT;
        break;
      case '3':
        car_state = TURN_RIGHT;
        break;
      case '4':
        car_state = TURN_LEFT_REVERSE;
        break;
      case '5':
        car_state = TURN_RIGHT_REVERSE;
        break;
      case '6':
        car_state = STOP;
        break;
    }
  }

  // MOVEMENTS
  switch (car_state) {
    case GO_FORWARD:
      go_forward ();
      break;
    case GO_BACKWARD:
      go_backward ();
      break;
    case TURN_LEFT:
      turn_left ();
      break;
    case TURN_RIGHT:
      turn_right ();
      break;
    case TURN_LEFT_REVERSE:
      turn_left_reverse ();
      break;
    case TURN_RIGHT_REVERSE:
      turn_right_reverse ();
      break;
    case STOP:
      stop_car ();
      break;
  }

  if (car_state != STOP) {
    // OBSTACLE DETECTION
    distance = sonar_distance (FRONT_SONAR_TRIG_PIN, FRONT_SONAR_ECHO_PIN);
    
    for (i = DISTANCE_ARRAY_SIZE - 1; i > 0; i--) obstacle_distance [i] = obstacle_distance [i - 1];
    obstacle_distance [0] = distance;

    overtake_start = true;
    for (i = 0; i < DISTANCE_ARRAY_SIZE; i++) {
      overtake_start = overtake_start && (obstacle_distance [i] < MAX_OBSTACLE_DISTANCE);
    }
    if (overtake_start) bt.write (OVERTAKE_START + '0');

  
    // OVERTAKE LATERAL DISTANCE
    distance = sonar_distance (RIGHT_SONAR_TRIG_PIN, RIGHT_SONAR_ECHO_PIN);
    
    for (i = DISTANCE_ARRAY_SIZE - 1; i > 0; i--) lateral_distance [i] = lateral_distance [i - 1];
    lateral_distance [0] = distance;

    overtake_end = true;
    for (i = 0; i < DISTANCE_ARRAY_SIZE; i++) {
      overtake_end = overtake_end && (lateral_distance [i] > MAX_LATERAL_DISTANCE);
    }
    bt.write ((overtake_end) ? "Y" : "N");
  
    delay (50);

    Serial.println (obstacle_distance [0]);
  }
}



// CAR MOVEMENTS
void go_forward () {
  digitalWrite (LEFT_MOTORS_DIRA_PIN,  HIGH);
  digitalWrite (LEFT_MOTORS_DIRB_PIN,  LOW);
  digitalWrite (RIGHT_MOTORS_DIRA_PIN, HIGH);
  digitalWrite (RIGHT_MOTORS_DIRB_PIN, LOW);
}

void go_backward () {
  digitalWrite (LEFT_MOTORS_DIRA_PIN,  LOW);
  digitalWrite (LEFT_MOTORS_DIRB_PIN,  HIGH);
  digitalWrite (RIGHT_MOTORS_DIRA_PIN, LOW);
  digitalWrite (RIGHT_MOTORS_DIRB_PIN, HIGH);
}

void turn_right () {
  digitalWrite (LEFT_MOTORS_DIRA_PIN,  HIGH);
  digitalWrite (LEFT_MOTORS_DIRB_PIN,  LOW);
  digitalWrite (RIGHT_MOTORS_DIRA_PIN, LOW);
  digitalWrite (RIGHT_MOTORS_DIRB_PIN, LOW); 
}

void turn_left () {
  digitalWrite (LEFT_MOTORS_DIRA_PIN,  LOW);
  digitalWrite (LEFT_MOTORS_DIRB_PIN,  LOW);
  digitalWrite (RIGHT_MOTORS_DIRA_PIN, HIGH);
  digitalWrite (RIGHT_MOTORS_DIRB_PIN, LOW);
}

void turn_right_reverse () {
  digitalWrite (LEFT_MOTORS_DIRA_PIN,  LOW);
  digitalWrite (LEFT_MOTORS_DIRB_PIN,  HIGH);
  digitalWrite (RIGHT_MOTORS_DIRA_PIN, LOW);
  digitalWrite (RIGHT_MOTORS_DIRB_PIN, LOW);
}

void turn_left_reverse () {
  digitalWrite (LEFT_MOTORS_DIRA_PIN,  LOW);
  digitalWrite (LEFT_MOTORS_DIRB_PIN,  LOW);
  digitalWrite (RIGHT_MOTORS_DIRA_PIN, LOW);
  digitalWrite (RIGHT_MOTORS_DIRB_PIN, HIGH);
}

void stop_car () {
  digitalWrite (LEFT_MOTORS_DIRA_PIN,  LOW);
  digitalWrite (LEFT_MOTORS_DIRB_PIN,  LOW);
  digitalWrite (RIGHT_MOTORS_DIRA_PIN, LOW);
  digitalWrite (RIGHT_MOTORS_DIRB_PIN, LOW);
}



// SONAR - OBSTACLE DISTANCE CALCULATION
int sonar_distance (int trig_pin, int echo_pin) {
  int distance;

  digitalWrite (trig_pin, LOW);
  delayMicroseconds (2);
  digitalWrite (trig_pin, HIGH);
  delayMicroseconds (10);
  digitalWrite (trig_pin, LOW);
    
  distance = (pulseIn (echo_pin, HIGH, 3000) / 2) * 0.034; // 0.034 cm/us = 340 m/s

  if (distance == 0) {
    pinMode (echo_pin, OUTPUT);
    digitalWrite (echo_pin, LOW);
    delay (10);
    pinMode (echo_pin, INPUT);
    
    distance = 1000;
  }

  return distance;
}
