/**
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not
 * use this file except in compliance with the License. A copy of the License
 * is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */

package com.amazon.deequ.suggestions.rules

import com.amazon.deequ.statistics.{DataType, DataTypeInstances}
import com.amazon.deequ.checks.Check
import com.amazon.deequ.constraints.{ConstrainableDataTypes, StatisticConstraint}
import com.amazon.deequ.metrics.Distribution
import com.amazon.deequ.profiles.ColumnProfile
import com.amazon.deequ.suggestions.ConstraintSuggestion

/** If we detect a non-string type, we suggest a type constraint */
case class RetainTypeRule() extends ConstraintRule[ColumnProfile] {

  override def shouldBeApplied(profile: ColumnProfile, numRecords: Long): Boolean = {
    val testableType = profile.dataType match {
      case DataTypeInstances.Integral | DataTypeInstances.Fractional | DataTypeInstances.Boolean =>
        true
      case _ => false
    }

    profile.isDataTypeInferred && testableType
  }

  override def candidate(profile: ColumnProfile, numRecords: Long): ConstraintSuggestion = {

    val typeToCheck = profile.dataType match {
      case DataTypeInstances.Fractional => ConstrainableDataTypes.Fractional
      case DataTypeInstances.Integral => ConstrainableDataTypes.Integral
      case DataTypeInstances.Boolean => ConstrainableDataTypes.Boolean
    }

    //TODO duplicates code from Check
    def valuePicker(distribution: Distribution): Double = {

      typeToCheck match {
        case ConstrainableDataTypes.Null => ratio(DataTypeInstances.Unknown, distribution)
        case ConstrainableDataTypes.Fractional => ratio(DataTypeInstances.Fractional, distribution)
        case ConstrainableDataTypes.Integral => ratio(DataTypeInstances.Integral, distribution)
        case ConstrainableDataTypes.Boolean => ratio(DataTypeInstances.Boolean, distribution)
        case ConstrainableDataTypes.String => ratio(DataTypeInstances.String, distribution)
        case ConstrainableDataTypes.Numeric =>
          ratio(DataTypeInstances.Fractional, distribution) +
            ratio(DataTypeInstances.Integral, distribution)
      }
    }


    val constraint =
      StatisticConstraint[Distribution, Double](DataType(profile.column), Check.IsOne, Some(valuePicker))

    ConstraintSuggestion(
      constraint,
      profile.column,
      "DataType: " + profile.dataType.toString,
      s"'${profile.column}' has type ${profile.dataType}",
      this,
      s""".hasDataType("${profile.column}", ConstrainableDataTypes.${profile.dataType})"""
    )
  }

  override val ruleDescription: String = "If we detect a non-string type, we suggest a type constraint"

  private[this] def ratio(keyType: DataTypeInstances.Value, distribution: Distribution): Double = {
    distribution.values.get(keyType.toString)
    .map { _.ratio }
    .getOrElse(0.0)
  }
}

